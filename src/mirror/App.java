package mirror;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public final class App extends JFrame {
    static final Color BG=new Color(241,245,249),INK=new Color(24,42,65),BLUE=new Color(36,99,235);
    final ServerPanel a=new ServerPanel("SERVEUR A", "localhost", "mirror_a"),b=new ServerPanel("SERVEUR B", "localhost", "mirror_b");
    final JTextField tables=new JTextField("contacts",18);
    final JSpinner interval=new JSpinner(new SpinnerNumberModel(30,5,3600,5));
    final JCheckBox demo=new JCheckBox("Mode démonstration (aucune écriture MySQL)",true);
    final JLabel state=new JLabel("●  Non activé"),detail=new JLabel("Configurez vos serveurs ou essayez le mode démonstration."),last=new JLabel("Dernier cycle : —"),count=new JLabel("0 écriture");
    final JProgressBar progress=new JProgressBar(0,100);
    final JTextArea log=new JTextArea();
    final JButton start=new JButton("Activer"),pause=new JButton("Mettre en veille"),stop=new JButton("Désactiver"),now=new JButton("Synchroniser maintenant"),save=new JButton("Enregistrer");
    final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"mirror-sync");t.setDaemon(true);return t;});
    final javax.swing.Timer timer;
    volatile boolean cancel=false;
    boolean enabled=false,sleeping=false,busy=false;
    long next=0; int written=0;
    final Path config=Paths.get(System.getProperty("user.home"),".Demo_Java_mysql_Mirror_Server.properties");
    public App() {
        super("Demo_Java_mysql_Mirror_Server");
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE); setMinimumSize(new Dimension(950,750)); setSize(1120,860); setLocationRelativeTo(null);
        JPanel root=new JPanel(new BorderLayout(0,18));root.setBackground(BG);root.setBorder(new EmptyBorder(22,28,14,28));setContentPane(root);
        JPanel header=new JPanel(new BorderLayout());header.setOpaque(false);
        JLabel title=new JLabel("MySQL  /  Mirror Server");title.setFont(new Font("Segoe UI",Font.BOLD,28));title.setForeground(INK);
        header.add(title,BorderLayout.NORTH);header.add(new JLabel("Demo_Java_mysql_Mirror_Server  •  Swing 1.1  •  Synchronisation bidirectionnelle A ↔ B"),BorderLayout.SOUTH);root.add(header,BorderLayout.NORTH);
        JPanel main=new JPanel();main.setOpaque(false);main.setLayout(new BoxLayout(main,BoxLayout.Y_AXIS));root.add(main,BorderLayout.CENTER);
        JPanel servers=new JPanel(new GridLayout(1,2,18,0));servers.setOpaque(false);servers.add(a);servers.add(b);main.add(servers);main.add(Box.createVerticalStrut(14));
        JPanel settings=card();settings.setLayout(new FlowLayout(FlowLayout.LEFT,10,8));settings.add(new JLabel("Tables :"));settings.add(tables);settings.add(new JLabel("Intervalle (s) :"));settings.add(interval);settings.add(save);settings.add(demo);main.add(settings);main.add(Box.createVerticalStrut(14));
        JPanel status=card();status.setLayout(new BorderLayout(8,12));state.setFont(new Font("Segoe UI",Font.BOLD,22));state.setForeground(BLUE);status.add(state,BorderLayout.NORTH);
        JPanel middle=new JPanel(new BorderLayout(0,8));middle.setOpaque(false);middle.add(detail,BorderLayout.NORTH);progress.setStringPainted(true);progress.setString("Prêt");progress.setPreferredSize(new Dimension(400,24));middle.add(progress,BorderLayout.CENTER);
        JPanel stats=new JPanel(new BorderLayout());stats.setOpaque(false);stats.add(last,BorderLayout.WEST);stats.add(count,BorderLayout.EAST);middle.add(stats,BorderLayout.SOUTH);status.add(middle,BorderLayout.CENTER);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));actions.setOpaque(false);for(JButton button:new JButton[]{start,now,pause,stop})actions.add(button);status.add(actions,BorderLayout.SOUTH);main.add(status);main.add(Box.createVerticalStrut(14));
        JPanel journal=card();journal.setLayout(new BorderLayout(0,8));journal.add(new JLabel("JOURNAL D’ACTIVITÉ"),BorderLayout.NORTH);log.setEditable(false);log.setFont(new Font("Consolas",Font.PLAIN,12));log.setLineWrap(true);log.setWrapStyleWord(true);journal.add(new JScrollPane(log),BorderLayout.CENTER);journal.setPreferredSize(new Dimension(900,180));main.add(journal);
        JLabel footer=new JLabel("Développeur Kalema Daniel Jonathan, kalemadaniel2014@gmail.com.",SwingConstants.CENTER);footer.setForeground(new Color(78,94,112));root.add(footer,BorderLayout.SOUTH);
        start.addActionListener(e->{enabled=true;sleeping=false;cancel=false;cycle();});
        now.addActionListener(e->cycle());
        pause.addActionListener(e->{sleeping=!sleeping;cancel=sleeping;pause.setText(sleeping?"Reprendre":"Mettre en veille");if(sleeping){setState("En veille");detail.setText(busy?"Mise en veille après l’opération actuelle…":"Synchronisation suspendue.");}else{next=0;if(!busy)cycle();else{setState("En cours");detail.setText("Reprise du cycle en cours…");}}updateButtons();});
        stop.addActionListener(e->{enabled=false;sleeping=false;cancel=true;pause.setText("Mettre en veille");setState("Non activé");detail.setText(busy?"Arrêt après l’opération actuelle…":"Synchronisation désactivée.");updateButtons();});
        save.addActionListener(e->save());demo.addActionListener(e->updateButtons());
        a.test.addActionListener(e->test(a));b.test.addActionListener(e->test(b));
        timer=new javax.swing.Timer(500,e->{if(enabled&&!sleeping&&!busy){long remaining=Math.max(0,(next-System.currentTimeMillis()+999)/1000);if(remaining==0)cycle();else detail.setText("Prochain cycle dans "+remaining+" s"+(demo.isSelected()?" • DÉMONSTRATION":""));}});timer.start();
        addWindowListener(new WindowAdapter(){public void windowClosing(WindowEvent e){cancel=true;enabled=false;timer.stop();worker.shutdown();dispose();}});
        load();updateButtons();append("Bienvenue. Le mode démonstration est activé. Les mots de passe ne sont jamais enregistrés.");
    }
    static JPanel card(){JPanel p=new JPanel();p.setBackground(Color.WHITE);p.setBorder(new CompoundBorder(new LineBorder(new Color(220,227,237)),new EmptyBorder(14,14,14,14)));return p;}
    void setState(String text){state.setText("●  "+text);state.setForeground(text.equals("Erreur")?new Color(190,48,48):text.equals("En cours")?BLUE:new Color(46,112,103));}
    void append(String text){log.append(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))+"  "+text+"\n");if(log.getDocument().getLength()>60000)log.replaceRange("",0,15000);log.setCaretPosition(log.getDocument().getLength());}
    void updateButtons(){start.setEnabled(!enabled&&!busy);now.setEnabled(enabled&&!sleeping&&!busy);pause.setEnabled(enabled);stop.setEnabled(enabled);save.setEnabled(!busy&&!enabled);demo.setEnabled(!busy&&!enabled);tables.setEnabled(!busy&&!enabled);interval.setEnabled(!busy&&!enabled);a.editable(!busy&&!enabled);b.editable(!busy&&!enabled);}
    void cycle(){
        if(busy||!enabled||sleeping)return;
        final boolean simulation=demo.isSelected();final SyncEngine.Endpoint ea,eb;
        try{ea=simulation?null:a.endpoint();eb=simulation?null:b.endpoint();}catch(Exception ex){enabled=false;setState("Erreur");append(ex.getMessage());updateButtons();return;}
        final String selected=tables.getText().trim();final int seconds=(Integer)interval.getValue();
        busy=true;cancel=false;updateButtons();setState("En cours");progress.setValue(0);progress.setIndeterminate(true);progress.setString("Connexion et lecture…");detail.setText(simulation?"DÉMONSTRATION • données simulées":"Connexion aux deux serveurs…");append(simulation?"Début d’un cycle simulé.":"Début de synchronisation A ↔ B.");
        worker.submit(()->{
            int changes=0;Exception failure=null;
            try{
                if(simulation){for(int i=1;i<=80&&!cancel;i++){Thread.sleep(60);final int n=i;SwingUtilities.invokeLater(()->{progress.setIndeterminate(false);progress.setValue(n*100/80);progress.setString("Démonstration • "+n+" / 80 lignes");});}}
                else changes=SyncEngine.sync(ea,eb,selected,()->cancel,new SyncEngine.Monitor(){public void log(String text){SwingUtilities.invokeLater(()->append(text));}public void progress(int done,int total,String text){SwingUtilities.invokeLater(()->{progress.setIndeterminate(false);progress.setValue(done*100/total);progress.setString(text);});}});
            }catch(Exception ex){failure=ex;}
            final int result=changes;final Exception error=failure;
            SwingUtilities.invokeLater(()->{
                busy=false;progress.setIndeterminate(false);next=System.currentTimeMillis()+seconds*1000L;
                if(error!=null){setState("Erreur");progress.setString("Cycle interrompu");a.status.setText("À vérifier");b.status.setText("À vérifier");append(error instanceof ClassNotFoundException?"Pilote MySQL absent : placez mysql-connector-j dans le dossier lib.":"Échec : "+safeError(error));detail.setText("Échec du cycle ; nouvel essai automatique si activé.");}
                else if(!cancel){written+=result;count.setText(written+" écritures MySQL");progress.setValue(100);progress.setString(simulation?"Démonstration terminée":"Cycle terminé");last.setText("Dernier cycle : "+LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));a.status.setText(simulation?"Simulé":"Connecté au dernier cycle");b.status.setText(simulation?"Simulé":"Connecté au dernier cycle");setState("En attente");append(simulation?"Simulation terminée. Aucune donnée MySQL modifiée.":"Cycle terminé : "+result+" écriture(s) validée(s).");}
                else{progress.setString("Interrompu");append("Cycle interrompu ; les écritures déjà validées sont conservées.");}
                if(!enabled)setState("Non activé");else if(sleeping)setState("En veille");updateButtons();
            });
        });
    }
    String safeError(Exception e){String m=e.getMessage();if(m==null)m=e.getClass().getSimpleName();for(ServerPanel p:new ServerPanel[]{a,b}){String pw=new String(p.password.getPassword());if(!pw.isEmpty())m=m.replace(pw,"***");}return m;}
    void test(ServerPanel panel){
        final SyncEngine.Endpoint ep;try{ep=panel.endpoint();}catch(Exception ex){append(ex.getMessage());return;}
        busy=true;updateButtons();panel.status.setText("Connexion…");worker.submit(()->{Exception failure=null;try{SyncEngine.test(ep);}catch(Exception ex){failure=ex;}final Exception error=failure;SwingUtilities.invokeLater(()->{busy=false;panel.status.setText(error==null?"Connexion réussie":"Erreur de connexion");append(panel.name+" : "+(error==null?"connexion réussie.":error instanceof ClassNotFoundException?"pilote MySQL absent (dossier lib).":safeError(error)));updateButtons();});});
    }
    void save(){Properties p=new Properties();a.store(p,"a.");b.store(p,"b.");p.setProperty("tables",tables.getText());p.setProperty("interval",interval.getValue().toString());try(OutputStream out=Files.newOutputStream(config)){p.store(out,"Mirror Server - passwords are never stored");append("Configuration enregistrée sans mots de passe.");}catch(IOException ex){append("Enregistrement impossible : "+ex.getMessage());}}
    void load(){if(!Files.exists(config))return;Properties p=new Properties();try(InputStream in=Files.newInputStream(config)){p.load(in);a.load(p,"a.");b.load(p,"b.");tables.setText(p.getProperty("tables","contacts"));int sec=Integer.parseInt(p.getProperty("interval","30"));interval.setValue(Math.max(5,Math.min(3600,sec)));}catch(Exception ex){append("Configuration non chargée ; vérifiez les champs.");}}
    static final class ServerPanel extends JPanel {
        final String name;final JTextField host,port=new JTextField("3306"),db,user=new JTextField("mirror_user");final JPasswordField password=new JPasswordField();final JComboBox<String> ssl=new JComboBox<>(new String[]{"VERIFY_IDENTITY","REQUIRED","DISABLED"});final JLabel status=new JLabel("Non testé");final JButton test=new JButton("Tester la connexion");
        ServerPanel(String name,String h,String d){this.name=name;host=new JTextField(h);db=new JTextField(d);setBackground(Color.WHITE);setBorder(new CompoundBorder(new LineBorder(new Color(220,227,237)),new EmptyBorder(14,14,14,14)));setLayout(new BorderLayout(0,10));JLabel title=new JLabel(name);title.setFont(new Font("Segoe UI",Font.BOLD,16));add(title,BorderLayout.NORTH);JPanel form=new JPanel(new GridLayout(6,2,8,7));form.setOpaque(false);String[] labels={"Hôte / adresse IP","Port MySQL","Base de données","Utilisateur","Mot de passe","Sécurité TLS"};JComponent[] inputs={host,port,db,user,password,ssl};for(int i=0;i<labels.length;i++){form.add(new JLabel(labels[i]));form.add(inputs[i]);}add(form,BorderLayout.CENTER);JPanel foot=new JPanel(new BorderLayout());foot.setOpaque(false);foot.add(status,BorderLayout.WEST);foot.add(test,BorderLayout.EAST);add(foot,BorderLayout.SOUTH);}
        SyncEngine.Endpoint endpoint(){
            int number;
            try{number=Integer.parseInt(port.getText().trim());}
            catch(NumberFormatException ex){throw new IllegalArgumentException(name+" : le port doit être un nombre entre 1 et 65535.");}
            return new SyncEngine.Endpoint(host.getText().trim(),number,db.getText().trim(),user.getText().trim(),new String(password.getPassword()),(String)ssl.getSelectedItem());
        }
        void editable(boolean on){for(JComponent c:new JComponent[]{host,port,db,user,password,ssl,test})c.setEnabled(on);}
        void store(Properties p,String prefix){p.setProperty(prefix+"host",host.getText());p.setProperty(prefix+"port",port.getText());p.setProperty(prefix+"db",db.getText());p.setProperty(prefix+"user",user.getText());p.setProperty(prefix+"ssl",(String)ssl.getSelectedItem());}
        void load(Properties p,String prefix){host.setText(p.getProperty(prefix+"host",host.getText()));port.setText(p.getProperty(prefix+"port","3306"));db.setText(p.getProperty(prefix+"db",db.getText()));user.setText(p.getProperty(prefix+"user","mirror_user"));String mode=p.getProperty(prefix+"ssl","VERIFY_IDENTITY");if(Arrays.asList("VERIFY_IDENTITY","REQUIRED","DISABLED").contains(mode))ssl.setSelectedItem(mode);}
    }
    public static void main(String[] args){SwingUtilities.invokeLater(()->{try{for(UIManager.LookAndFeelInfo info:UIManager.getInstalledLookAndFeels())if("Nimbus".equals(info.getName()))UIManager.setLookAndFeel(info.getClassName());}catch(Exception ignored){}UIManager.put("defaultFont",new Font("Segoe UI",Font.PLAIN,13));new App().setVisible(true);});}
}
