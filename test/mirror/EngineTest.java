package mirror;
import java.sql.*;
import java.util.*;
import java.lang.reflect.*;

/** Dependency-free checks for merge rules and transactional concurrent-edit protection. */
public final class EngineTest {
    static int checks=0;
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;}
    static List<Object> value(String name,String time,int deleted){return Arrays.<Object>asList("id-1",name,Timestamp.valueOf(time),deleted);}
    static final class Db implements InvocationHandler {
        List<Object> current; boolean committed,rolledBack,fail;int writes;boolean auto=true;
        Db(List<Object> current){this.current=current;}
        @SuppressWarnings("unchecked") <T>T proxy(Class<T> cls){return (T)Proxy.newProxyInstance(cls.getClassLoader(),new Class<?>[]{cls},this);}
        public Object invoke(Object proxy,Method m,Object[] a)throws Throwable{
            String n=m.getName();
            if(n.equals("prepareStatement"))return proxy(PreparedStatement.class);
            if(n.equals("executeQuery")){
                final boolean[] seen={false};
                return Proxy.newProxyInstance(ResultSet.class.getClassLoader(),new Class<?>[]{ResultSet.class},(p,method,args)->{
                    if(method.getName().equals("next")){boolean result=!seen[0]&&current!=null;seen[0]=true;return result;}
                    if(method.getName().equals("getObject"))return current.get((Integer)args[0]-1);
                    return null;
                });
            }
            if(n.equals("executeUpdate")){if(fail)throw new SQLException("injected failure");writes++;return 1;}
            if(n.equals("commit"))committed=true;
            if(n.equals("rollback"))rolledBack=true;
            if(n.equals("setAutoCommit"))auto=(Boolean)a[0];
            return null;
        }
    }
    public static void main(String[] args)throws Exception{
        List<Object> older=value("ancien","2026-01-01 12:00:00.000001",0), newer=value("récent","2026-01-01 12:00:00.000002",0),tomb=value("supprimé","2026-01-01 12:00:00.000003",1);
        check(SyncEngine.winner(older,newer,2)==newer,"B newer");
        check(SyncEngine.winner(newer,older,2)==newer,"A newer");
        check(SyncEngine.winner(null,newer,2)==newer,"Missing A");
        check(SyncEngine.winner(newer,null,2)==newer,"Missing B");
        check(SyncEngine.winner(older,tomb,2)==tomb,"Tombstone propagates");
        check(SyncEngine.winner(newer,value("conflict","2026-01-01 12:00:00.000002",1),2)==newer,"Tie favors A");
        SyncEngine.Schema s=new SyncEngine.Schema();s.names.addAll(Arrays.asList("id","nom","updated_at","is_deleted"));s.key=0;s.time=2;
        Db insert=new Db(null);check(SyncEngine.write(insert.proxy(Connection.class),"contacts",s,null,newer),"Insert result");check(insert.writes==1&&insert.committed&&insert.auto,"Insert commit");
        Db update=new Db(older);check(SyncEngine.write(update.proxy(Connection.class),"contacts",s,older,newer),"Update result");check(update.writes==1&&update.committed&&update.auto,"Update commit");
        Db race=new Db(tomb);check(!SyncEngine.write(race.proxy(Connection.class),"contacts",s,older,newer),"Concurrent edit skipped");check(race.writes==0&&race.rolledBack&&race.auto,"Concurrent edit preserved");
        Db broken=new Db(older);broken.fail=true;try{SyncEngine.write(broken.proxy(Connection.class),"contacts",s,older,newer);throw new AssertionError("Failure swallowed");}catch(SQLException expected){check(broken.rolledBack&&!broken.committed&&broken.auto,"Rollback on SQL error");}
        boolean invalid=false;try{new SyncEngine.Endpoint("host?x=1",3306,"db","user","","REQUIRED");}catch(IllegalArgumentException expected){invalid=true;}check(invalid,"Host URL injection rejected");
        System.out.println(checks+" checks passed.");
    }
}
