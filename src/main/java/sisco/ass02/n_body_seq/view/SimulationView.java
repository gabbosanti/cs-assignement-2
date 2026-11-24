package sisco.ass02.n_body_seq.view;

import sisco.ass02.n_body_seq.model.Body;
import sisco.ass02.n_body_seq.model.P2d;
import sisco.ass02.n_body_par.sync.Controller;

import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

import javax.swing.*;

/**
 * 
 * Simulation view
 *
 */
public class SimulationView {
        
	private VisualiserFrame frame;
    private long nFramesRendered;
    private long startTime;
    private final Controller controller;

    public SimulationView(int w, int h){
        this(w, h, null);
    }

    public SimulationView(int w, int h, Controller controller){
        this.controller = controller;
        Runnable createFrame = () -> frame = new VisualiserFrame(w, h, controller);
        if (SwingUtilities.isEventDispatchThread()) {
            createFrame.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(createFrame);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Creazione GUI interrotta", ex);
            } catch (java.lang.reflect.InvocationTargetException ex) {
                throw new IllegalStateException("Impossibile creare la GUI", ex.getCause());
            }
        }
    }

    public Controller getController() {
        return controller;
    }
    
    public void init() {
    	startTime = System.currentTimeMillis();
    	nFramesRendered = 0;	
    }
        
    public void display(List<Body> bodies, double vt, long iter){
    	nFramesRendered++;
    	long dt = Math.max(1, System.currentTimeMillis() - startTime);
    	int framePerSec = (int)((((double)nFramesRendered)/dt)*1000);
    	frame.display(bodies, vt, iter, framePerSec); 
    }
    
    public static class VisualiserFrame extends JFrame {

        private VisualiserPanel panel;

        public VisualiserFrame(int w, int h){
            this(w, h, null);
        }

        public VisualiserFrame(int w, int h, Controller controller){
            setTitle("Bodies Simulation");
            setSize(w,h);
            setResizable(false);
            panel = new VisualiserPanel(w,h);
            getContentPane().add(panel, BorderLayout.CENTER);
            setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            Runnable close = () -> {
                if (controller != null) controller.stop();
                dispose();
            };
            if (controller != null) {
                JButton pause = new JButton(controller.getPaused() ? "Riprendi (Space)" : "Pausa (Space)");
                Action togglePause = new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        controller.togglePause();
                        pause.setText(controller.getPaused() ? "Riprendi (Space)" : "Pausa (Space)");
                    }
                };
                Action step = new AbstractAction("Step (Right)") {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        controller.step();
                        pause.setText("Riprendi (Space)");
                    }
                };
                pause.addActionListener(togglePause);
                // Space e Right sono comandi della finestra anche col focus sui pulsanti.
                pause.setFocusable(false);
                JButton stepButton = new JButton(step);
                stepButton.setFocusable(false);
                JButton stopButton = new JButton("Chiudi (Q)");
                stopButton.setFocusable(false);
                stopButton.addActionListener(e -> close.run());
                JPanel controls = new JPanel();
                controls.add(pause);
                controls.add(stepButton);
                controls.add(stopButton);
                getContentPane().add(controls, BorderLayout.SOUTH);
                panel.bindKey("SPACE", "pause", togglePause);
                panel.bindKey("RIGHT", "step", step);
            }
            panel.bindKey("Q", "close", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    close.run();
                }
            });
            addWindowListener(new WindowAdapter(){
    			public void windowClosing(WindowEvent ev){
                    close.run();
    			}
    		});
    		this.setVisible(true);
        }
        
        public void display(List<Body> bodies, double vt, long iter, int framePerSec){
            Runnable update = () -> {
                if (!isDisplayable()) return;
                panel.display(bodies, vt, iter, framePerSec);
                repaint();
            };
            if (SwingUtilities.isEventDispatchThread()) {
                update.run();
                return;
            }
        	try {
                SwingUtilities.invokeAndWait(update);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (java.lang.reflect.InvocationTargetException ex) {
                ex.getCause().printStackTrace();
            }
        };
        
        public void updateScale(double k) {
        	panel.updateScale(k);
        }    	
    }

    public static class VisualiserPanel extends JPanel {
            	
    	private List<Body> bodies;
    	
    	private long nIter;
    	private int framePerSec = 0;
    	private double vt;
    	private double scale = 1;
    	
        private long dx;
        private long dy;
        
        
        public VisualiserPanel(int w, int h){
            setSize(w,h);
            dx = w/2 - 20;
            dy = h/2 - 20;
			setFocusable(true);
            bindKey("UP", "zoomIn", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    updateScale(1.1);
                }
            });
            bindKey("DOWN", "zoomOut", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    updateScale(0.9);
                }
            });
        }

        private void bindKey(String key, String name, Action action) {
            getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .put(KeyStroke.getKeyStroke(key), name);
            getActionMap().put(name, action);
        }

        @Override
        protected void paintComponent(Graphics g){
            super.paintComponent(g);
    		if (bodies != null) {
        		Graphics2D g2 = (Graphics2D) g;
        		
        		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
        		          RenderingHints.VALUE_ANTIALIAS_ON);
        		g2.setRenderingHint(RenderingHints.KEY_RENDERING,
        		          RenderingHints.VALUE_RENDER_QUALITY);
        		g2.clearRect(0,0,this.getWidth(),this.getHeight());
        		        		
	    		bodies.forEach( b -> {
	    			P2d p = b.getPos();
			        int radius = (int) (b.getRadius()*dx*scale);
			        if (radius < 1) {
			        	radius = 1;
			        }
			        g2.drawOval(getXcoord(p.getX()),getYcoord(p.getY()), radius, radius); 
			    });		    
	    		String time = String.format("%.2f", vt);
	    		g2.drawString("Bodies: " + bodies.size() + " - vt: " + time + " - nIter: " + nIter + " - frames/sec: " + framePerSec, 2, 20);
	    		g2.drawString("Commands: UP for zoom in, DOWN for zoom out", 2, 40);
    		}
        }
        
        private int getXcoord(double x) {
        	return (int)(dx + x*dx*scale);
        }

        private int getYcoord(double y) {
        	return (int)(dy - y*dy*scale);
        }
        
        public void display(List<Body> bodies, double vt, long iter, int framePerSec){
            this.bodies = bodies;
            this.vt = vt;
            this.nIter = iter;
            this.framePerSec = framePerSec;
        }
        
        public void updateScale(double k) {
        	scale *= k;
            repaint();
        }
    }
}
