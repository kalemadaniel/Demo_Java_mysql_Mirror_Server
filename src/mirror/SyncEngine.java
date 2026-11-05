package mirror;

import java.sql.*;
import java.util.*;
import java.util.function.*;

/** Small-table, bidirectional synchronizer. No DDL or physical deletes. */
public final class SyncEngine {
    public static final class Endpoint {
        public final String host, database, user, password, ssl;
        public final int port;
        public Endpoint(String h, int p, String d, String u, String pw, String s) {
            host=h; port=p; database=d; user=u; password=pw; ssl=s;
            if (!h.matches("[a-zA-Z0-9._-]+") || !d.matches("[a-zA-Z0-9_]+") || p<1 || p>65535 || u.trim().isEmpty())
                throw new IllegalArgumentException("Vérifiez l’hôte, le port, la base et l’utilisateur.");
        }
        Connection open() throws Exception {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Properties p=new Properties();
            p.setProperty("user",user); p.setProperty("password",password);
            p.setProperty("sslMode",ssl); p.setProperty("connectTimeout","7000");
            p.setProperty("socketTimeout","20000"); p.setProperty("connectionTimeZone","UTC");
            p.setProperty("forceConnectionTimeZoneToSession","true"); p.setProperty("preserveInstants","true");
            Connection c=DriverManager.getConnection("jdbc:mysql://"+host+":"+port+"/"+database,p);
            try (Statement st=c.createStatement()) { st.execute("SET SESSION innodb_lock_wait_timeout=5"); }
            catch (Exception e) { c.close(); throw e; }
            return c;
        }
    }
    public interface Monitor { void progress(int done,int total,String message); void log(String message); }
    static String q(String s) { return "`"+s.replace("`","``")+"`"; }
    static class Schema {
        final List<String> names=new ArrayList<>(), signatures=new ArrayList<>();
        int key=-1, time=-1;
    }
    static Schema schema(Connection c,String table) throws SQLException {
        Schema s=new Schema();
        try (PreparedStatement p=c.prepareStatement("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?")) {
            p.setString(1,table);
            try(ResultSet r=p.executeQuery()) { if(!r.next() || !"InnoDB".equalsIgnoreCase(r.getString(1))) throw new SQLException(table+" : une table InnoDB est requise."); }
        }
        String sql="SELECT COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,EXTRA,COLLATION_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? ORDER BY ORDINAL_POSITION";
        try(PreparedStatement p=c.prepareStatement(sql)) {
            p.setString(1,table);
            try(ResultSet r=p.executeQuery()) { while(r.next()) {
                String name=r.getString(1),type=r.getString(2).toLowerCase(Locale.ROOT),extra=r.getString(4);
                if(extra.contains("VIRTUAL GENERATED") || extra.contains("STORED GENERATED") || type.matches(".*(blob|binary|geometry|point|polygon|linestring|json|bit\\().*"))
                    throw new SQLException(table+" : type ou colonne calculée non pris en charge : "+name);
                s.names.add(name); s.signatures.add(name+"|"+type+"|"+r.getString(3)+"|"+extra+"|"+r.getString(5));
                if(name.equals("updated_at")) { s.time=s.names.size()-1; if(!type.equals("timestamp(6)") || !r.getString(3).equals("NO")) throw new SQLException("updated_at doit être TIMESTAMP(6) NOT NULL."); }
                if(name.equals("is_deleted") && (!type.startsWith("tinyint") || !r.getString(3).equals("NO"))) throw new SQLException("is_deleted doit être TINYINT NOT NULL.");
            }}
        }
        try(ResultSet r=c.getMetaData().getPrimaryKeys(c.getCatalog(),null,table)) {
            while(r.next()) { if(s.key!=-1) throw new SQLException(table+" : utilisez une clé primaire simple."); s.key=s.names.indexOf(r.getString("COLUMN_NAME")); }
        }
        if(s.key<0 || s.time<0 || !s.names.contains("is_deleted")) throw new SQLException(table+" : clé primaire, updated_at et is_deleted requis.");
        return s;
    }
    static List<Object> row(ResultSet r, Schema s) throws SQLException {
        List<Object> v=new ArrayList<>();
        for(int i=0;i<s.names.size();i++) v.add(r.getObject(i+1));
        return v;
    }
    static Map<Object,List<Object>> read(Connection c,String table,Schema s) throws SQLException {
        Map<Object,List<Object>> rows=new LinkedHashMap<>();
        try(Statement st=c.createStatement()) {
            st.setQueryTimeout(20);
            try(ResultSet r=st.executeQuery("SELECT "+columns(s)+" FROM "+q(table)+" LIMIT 10001")) {
                while(r.next()) { List<Object> v=row(r,s); rows.put(v.get(s.key),v); }
            }
        }
        if(rows.size()>10000) throw new SQLException(table+" : limite de démonstration de 10 000 lignes dépassée.");
        return rows;
    }
    static String columns(Schema s) { List<String> v=new ArrayList<>(); for(String n:s.names)v.add(q(n)); return String.join(",",v); }
    /** Equal timestamp conflicts favor A, consistently, including tombstones. */
    static List<Object> winner(List<Object> a,List<Object> b,int time) {
        if(a==null)return b; if(b==null)return a;
        return ((Timestamp)a.get(time)).compareTo((Timestamp)b.get(time))>=0?a:b;
    }
    static boolean write(Connection c,String table,Schema s,List<Object> expected,List<Object> value) throws SQLException {
        c.setAutoCommit(false);
        try {
            List<Object> current=null;
            try(PreparedStatement p=c.prepareStatement("SELECT "+columns(s)+" FROM "+q(table)+" WHERE "+q(s.names.get(s.key))+"=? FOR UPDATE")) {
                p.setObject(1,value.get(s.key)); p.setQueryTimeout(20);
                try(ResultSet r=p.executeQuery()) { if(r.next())current=row(r,s); }
            }
            // A concurrent local edit must survive; recompute in the next cycle.
            if(!Objects.equals(current,expected)) { c.rollback(); return false; }
            String sql;
            if(current==null) sql="INSERT INTO "+q(table)+" ("+columns(s)+") VALUES ("+String.join(",",Collections.nCopies(s.names.size(),"?"))+")";
            else { List<String> fields=new ArrayList<>(); for(String n:s.names)fields.add(q(n)+"=?"); sql="UPDATE "+q(table)+" SET "+String.join(",",fields)+" WHERE "+q(s.names.get(s.key))+"=?"; }
            try(PreparedStatement p=c.prepareStatement(sql)) {
                for(int i=0;i<value.size();i++)p.setObject(i+1,value.get(i));
                if(current!=null)p.setObject(value.size()+1,value.get(s.key));
                p.setQueryTimeout(20); p.executeUpdate();
            }
            c.commit(); return true;
        } catch(SQLException e) { c.rollback(); throw e; }
        finally { c.setAutoCommit(true); }
    }
    public static void test(Endpoint endpoint) throws Exception { try(Connection c=endpoint.open()) { if(!c.isValid(5))throw new SQLException("Connexion invalide."); } }
    public static int sync(Endpoint a,Endpoint b,String tables,BooleanSupplier stop,Monitor monitor) throws Exception {
        if(a.host.equalsIgnoreCase(b.host)&&a.port==b.port&&a.database.equals(b.database))throw new IllegalArgumentException("Choisissez deux bases distinctes.");
        String[] names=tables.split(",",-1);
        Set<String> unique=new LinkedHashSet<>();
        for(String n:names) { n=n.trim(); if(!n.matches("[a-zA-Z0-9_]+"))throw new IllegalArgumentException("Noms des tables invalides."); unique.add(n); }
        try(Connection ca=a.open();Connection cb=b.open()) {
            // Validate every selected schema before the first write.
            Map<String,Schema> schemas=new LinkedHashMap<>();
            for(String table:unique) {
                Schema sa=schema(ca,table),sb=schema(cb,table);
                if(!sa.signatures.equals(sb.signatures)||sa.key!=sb.key)throw new SQLException(table+" : les structures des deux tables diffèrent.");
                schemas.put(table,sa);
            }
            int writes=0, index=0;
            for(String table:unique) {
                if(stop.getAsBoolean())return writes;
                Schema s=schemas.get(table);
                monitor.log("Lecture de "+table+" sur A et B…");
                Map<Object,List<Object>> ra=read(ca,table,s),rb=read(cb,table,s);
                Set<Object> keys=new LinkedHashSet<>(ra.keySet()); keys.addAll(rb.keySet()); int done=0;
                for(Object key:keys) {
                    if(stop.getAsBoolean())return writes;
                    List<Object> va=ra.get(key),vb=rb.get(key),win=winner(va,vb,s.time);
                    if(va!=null&&vb!=null&&!va.equals(vb)&&va.get(s.time).equals(vb.get(s.time)))monitor.log("Conflit dans "+table+" : dates identiques, priorité à A.");
                    if(!Objects.equals(va,win)) { if(write(ca,table,s,va,win))writes++; else monitor.log("Modification concurrente sur A : report au prochain cycle."); }
                    if(!Objects.equals(vb,win)) { if(write(cb,table,s,vb,win))writes++; else monitor.log("Modification concurrente sur B : report au prochain cycle."); }
                    done++;
                    monitor.progress(index*100+(int)(100L*done/keys.size()),unique.size()*100,table+" • "+done+" / "+keys.size()+" lignes comparées");
                }
                index++; monitor.progress(index*100,unique.size()*100,table+" terminée");
            }
            return writes;
        }
    }
}
