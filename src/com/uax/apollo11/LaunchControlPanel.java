/*************************************
 Programación Concurrente
 Ejercicio Feedback 1
 Pablo Tornero Casas
 *************************************/

package com.uax.apollo11;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;

public class LaunchControlPanel extends JFrame {
    private JTextField secondsField;
    private JButton startButton, cancelButton;
    private JProgressBar progressBar;
    private JPanel[] phasePanels = new JPanel[4];
    private String[] phaseNames = {"Calentando Motores", "Comprobación del Sistema", "Cuenta Atrás", "Despegue"};
    private JPanel[] statePanels = new JPanel[3];
    private String[] stateNames = {"Activo", "Cancelado", "Completado"};
    private ExecutorService phaseExecutor;
    private ExecutorService monitorExecutor;
    private AtomicBoolean launchCancelled = new AtomicBoolean(false);

    public LaunchControlPanel() {
        prepareGUI();
    }

    private void prepareGUI() {
        setTitle("Panel de Control de Lanzamiento");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        add(createLeftPanel(), BorderLayout.WEST);
        add(createCenterPanel(), BorderLayout.CENTER);
        add(createRightPanel(), BorderLayout.EAST);

        progressBar = new JProgressBar();
        progressBar.setPreferredSize(new Dimension(getWidth(), 60));
        progressBar.setStringPainted(false);
        add(progressBar, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private JPanel createLeftPanel() {
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.add(new JLabel("Segundos para la cuenta atrás:"));
        secondsField = new JTextField("10", 10);
        leftPanel.add(secondsField);

        startButton = new JButton("Iniciar");
        startButton.addActionListener(e -> startLaunchSequence());
        leftPanel.add(startButton);

        cancelButton = new JButton("Cancelar");
        cancelButton.addActionListener(e -> cancelLaunch());
        leftPanel.add(cancelButton);

        return leftPanel;
    }

    private JPanel createCenterPanel() {
        JPanel centerPanel = new JPanel(new GridLayout(1, 4, 10, 10));
        for (int i = 0; i < phasePanels.length; i++) {
            phasePanels[i] = createPanelWithLabel(phaseNames[i], Color.GRAY);
            centerPanel.add(phasePanels[i]);
        }
        return centerPanel;
    }

    private JPanel createRightPanel() {
        JPanel rightPanel = new JPanel(new GridLayout(3, 1, 5, 5));
        for (int i = 0; i < statePanels.length; i++) {
            statePanels[i] = createPanelWithLabel(stateNames[i], Color.GRAY);
            rightPanel.add(statePanels[i]);
        }
        return rightPanel;
    }

    private JPanel createPanelWithLabel(String text, Color bg) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(bg);
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        panel.add(label);
        return panel;
    }

    private void startLaunchSequence() {
        if (phaseExecutor != null && !phaseExecutor.isShutdown()) {
            phaseExecutor.shutdownNow();
        }
        if (monitorExecutor != null && !monitorExecutor.isShutdown()) {
            monitorExecutor.shutdownNow();
        }
        phaseExecutor = Executors.newFixedThreadPool(1);
        monitorExecutor = Executors.newSingleThreadExecutor();
        launchCancelled.set(false);
        resetAllPanels();

        statePanels[0].setBackground(Color.GREEN); // Estado "Activo"
        startMonitor();
        // Al iniciar el proceso nos aseguramos de que el tiene el texto adecuado (problema con los inicios despues de iniciar una vez)
        JLabel label = (JLabel) phasePanels[2].getComponent(0);
        label.setText("Cuenta Atrás");

        // Fases de lanzamiento
        executePhase(0, "Calentando Motores", 3000);
        executePhase(1, "Comprobación del Sistema", 2000);
        executePhase(2, "Cuenta Atrás", 10); //  manejo dentro de countdownTask
        executePhase(3, "Despegue", 3000);
    }

    private void executePhase(int phaseIndex, String phaseName, long sleepTime) {
        phaseExecutor.submit(() -> {
            if (phaseIndex == 2) { // Manejo especial para "Cuenta Atrás"
                countdownPhase();
                phaseAction(phaseIndex, sleepTime);
            } else {
                playSound("resources/"+phaseIndex + ".wav");
                phaseAction(phaseIndex, sleepTime);

            }
            if (phaseIndex==3){ // Además si es la última fase tambíen hacemos lo siguiente
                SwingUtilities.invokeLater(() -> {
                    resetStatePanels();
                    statePanels[2].setBackground(Color.CYAN); // Muestra "Completado"

                    Icon thumbsUpIcon = new ImageIcon(getClass().getResource("/resources/Completado.png"));
                    JLabel messageLabel = new JLabel();
                    messageLabel.setIcon(thumbsUpIcon);
                    // Mostrar ventana emergente
                    JOptionPane.showMessageDialog(this, messageLabel, "Completado", JOptionPane.INFORMATION_MESSAGE);
                });
            }
        });
    }

    // la fase countdown es diferente
    private void countdownPhase() {
        SwingUtilities.invokeLater(() -> phasePanels[2].setBackground(Color.GREEN));
        int seconds = Integer.parseInt(secondsField.getText().trim());
        SwingUtilities.invokeLater(() -> progressBar.setMaximum(seconds));
        countdownTask(seconds);
    }

    // por cada fase ejecutamos el cambio de color antes y después de la fase
    private void phaseAction(int phaseIndex, long sleepTime) {
        SwingUtilities.invokeLater(() -> phasePanels[phaseIndex].setBackground(Color.GREEN));
        try {
            if (sleepTime > 0) {
                Thread.sleep(sleepTime);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        SwingUtilities.invokeLater(() -> phasePanels[phaseIndex].setBackground(Color.GRAY));
    }

    // fase de cuenta atrás, sustitucion del nombre por los números tb
    private void countdownTask(int seconds) {
        for (int i = seconds; i >= 0; i--) {
            if (launchCancelled.get()) {
                break;
            }
            final int progress = seconds - i;
            final String countdownText = String.valueOf(i);
            SwingUtilities.invokeLater(() -> {
                progressBar.setValue(progress);
                JLabel label = (JLabel) phasePanels[2].getComponent(0);
                label.setText(countdownText);
            });
            // antes de 3 segungos un sonido
            if (i > 3) {
                playSound("resources/beep.wav");
            }
            // últimos 3 segundos sonido diferente
            if (i == 3) {
                playSound("resources/2.wav");
            }
            // para que no sea tan rápido
            try {
                Thread.sleep(1000); // Espera 1 segundo
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    // si se pulsa cancelación,
    private void cancelLaunch() {
        launchCancelled.set(true);
        if (!phaseExecutor.isShutdown()) {
            phaseExecutor.shutdownNow();
        }
        if (!monitorExecutor.isShutdown()) {
            monitorExecutor.shutdownNow();
        }
        SwingUtilities.invokeLater(() -> {
            resetAllPanels();
            statePanels[1].setBackground(Color.RED); // Muestra "Cancelado"
            // Mostrar ventana emergente de cancelación
            Icon thumbsUpIcon = new ImageIcon(getClass().getResource("/resources/Cancelado.png"));
            JLabel messageLabel = new JLabel();
            messageLabel.setIcon(thumbsUpIcon);
            // Mostrar ventana emergente
            JOptionPane.showMessageDialog(this, messageLabel, "Cancelado", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    // resetea Paneles
    private void resetAllPanels() {
        resetPhasePanels();
        resetStatePanels();
        progressBar.setValue(0);
    }
    // resetea Estados a grises
    private void resetStatePanels() {
        for (JPanel panel : statePanels) {
            panel.setBackground(Color.GRAY);
        }
    }
    // resetea Fases a grises
    private void resetPhasePanels() {
        for (JPanel panel : phasePanels) {
            panel.setBackground(Color.GRAY);
        }
    }

    private void startMonitor() {
        monitorExecutor.submit(() -> {
            while (!phaseExecutor.isShutdown() || !phaseExecutor.isTerminated()) {
                try {

                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }

        });
    }
// salida del sonido
    private void playSound(String soundFileName) {
        try {
            AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(getClass().getResource("/" + soundFileName));
            Clip clip = AudioSystem.getClip();
            clip.open(audioInputStream);
            clip.start();
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            e.printStackTrace();
        }
    }

// función principal
    public static void main(String[] args) {
        SwingUtilities.invokeLater(LaunchControlPanel::new);
    }
}
