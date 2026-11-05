package mirror;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import javax.imageio.ImageIO;
import java.io.File;

/** Swing component tests; renders the application's own component tree off screen. */
public final class UiTest {
    static App app;
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void layout(java.awt.Container c){c.doLayout();for(java.awt.Component child:c.getComponents())if(child instanceof java.awt.Container)layout((java.awt.Container)child);}
    public static void main(String[] args)throws Exception{
        try{
            SwingUtilities.invokeAndWait(()->{
                try { UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel"); }catch(Exception ignored){}
                app=new App();layout(app);check(app.state.getText().contains("Non activé"),"Initial state");app.start.doClick();check(app.busy,"Worker started");check(!app.start.isEnabled(),"No overlapping cycle");
            });
            Thread.sleep(1200);
            SwingUtilities.invokeAndWait(()->{
                check(app.progress.getValue()>0&&app.progress.getValue()<100,"Progress moves");
                if(args.length>0)try{
                    layout(app);BufferedImage image=new BufferedImage(app.getRootPane().getWidth(),app.getRootPane().getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();app.getRootPane().printAll(g);g.dispose();ImageIO.write(image,"png",new File(args[0]));
                }catch(Exception e){throw new RuntimeException(e);}
                app.pause.doClick();check(app.sleeping&&app.cancel,"Pause requests cancellation");
            });
            Thread.sleep(300);
            SwingUtilities.invokeAndWait(()->{check(!app.busy,"Pause completed");check(app.state.getText().contains("En veille"),"Sleep state");app.pause.doClick();check(app.busy&&!app.sleeping,"Resume starts new cycle");});
            Thread.sleep(5500);
            SwingUtilities.invokeAndWait(()->{check(!app.busy,"Cycle completes");check(app.progress.getValue()==100,"Progress completed");check(app.state.getText().contains("En attente"),"Waiting state");check(app.written==0,"Demo never counts real writes");app.stop.doClick();check(!app.enabled&&app.state.getText().contains("Non activé"),"Stop state");});
            System.out.println("13 Swing checks passed.");
        }finally{SwingUtilities.invokeAndWait(()->{if(app!=null){app.cancel=true;app.timer.stop();app.worker.shutdownNow();app.dispose();}});}
    }
}
