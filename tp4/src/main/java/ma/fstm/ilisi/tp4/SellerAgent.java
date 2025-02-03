package ma.fstm.ilisi.tp4;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.util.*;

/**
 * SellerAgent is a JADE agent that manages the selling process in an auction.
 * It handles the publication of offers, starts auctions, and processes bids from buyers.
 */
public class SellerAgent extends Agent {
    private JFrame frame;
    private JTextArea logArea;
    private JButton publishButton;
    private JButton startButton;
    private JPanel proposalsPanel;
    private String item;
    private double minPrice;
    private boolean auctionInProgress = false;
    private Timer auctionTimer;
    private int timeLeft = 60;
    private JLabel timerLabel;
    private JTextField itemField;
    private JTextField priceField;
    private JTextField timeField;
    private final Map<AID, Double> currentProposals = new HashMap<>();

    /**
     * Setup method is called when the agent is initialized.
     * It creates the GUI and adds the main behavior for handling messages.
     */
    protected void setup() {
        createGUI();

        addBehaviour(new CyclicBehaviour() {
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    switch(msg.getPerformative()) {
                        case ACLMessage.PROPOSE:
                            double bid = Double.parseDouble(msg.getContent());
                            currentProposals.put(msg.getSender(), bid);
                            updateProposalsPanel();
                            break;

                        case ACLMessage.REQUEST: // Request for more time
                            if (auctionInProgress) {
                                timeLeft += 30;
                                timerLabel.setText("Time left: " + timeLeft + "s");
                                ACLMessage timeUpdate = new ACLMessage(ACLMessage.INFORM);
                                currentProposals.keySet().forEach(timeUpdate::addReceiver);
                                timeUpdate.setContent("TIME_EXTENDED;" + timeLeft);
                                send(timeUpdate);
                                logArea.append("Time extended by 30 seconds. New time: " + timeLeft + "s\n");
                            }
                            break;
                    }
                }
                block();
            }
        });
    }

    /**
     * Creates the GUI for the seller agent.
     */
    private void createGUI() {
        frame = new JFrame("Seller: " + getLocalName());
        frame.setLayout(new BorderLayout(10, 10));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Create panels with borders
        JPanel inputPanel = new JPanel(new GridBagLayout());
        inputPanel.setBorder(BorderFactory.createTitledBorder("Auction Configuration"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 5, 5, 5);

        // Input fields
        inputPanel.add(new JLabel("Item:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        itemField = new JTextField(15);
        inputPanel.add(itemField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        inputPanel.add(new JLabel("Minimum Price:"), gbc);
        gbc.gridx = 1;
        priceField = new JTextField(15);
        inputPanel.add(priceField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        inputPanel.add(new JLabel("Time (seconds):"), gbc);
        gbc.gridx = 1;
        timeField = new JTextField("60", 15);
        inputPanel.add(timeField, gbc);

        // Buttons panel
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        JPanel buttonPanel = new JPanel();
        publishButton = new JButton("Publish Offer");
        startButton = new JButton("Start Auction");
        startButton.setEnabled(false);
        buttonPanel.add(publishButton);
        buttonPanel.add(startButton);
        inputPanel.add(buttonPanel, gbc);

        // Timer label
        gbc.gridy = 4;
        timerLabel = new JLabel("Time left: --");
        inputPanel.add(timerLabel, gbc);

        // Proposals panel
        proposalsPanel = new JPanel();
        proposalsPanel.setLayout(new BoxLayout(proposalsPanel, BoxLayout.Y_AXIS));
        proposalsPanel.setBorder(BorderFactory.createTitledBorder("Current Proposals"));
        JScrollPane proposalsScroll = new JScrollPane(proposalsPanel);
        proposalsScroll.setPreferredSize(new Dimension(300, 200));

        // Log panel
        logArea = new JTextArea(10, 40);
        logArea.setEditable(false);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Auction Log"));

        // Main content panel
        JPanel contentPanel = new JPanel(new BorderLayout(10, 10));
        contentPanel.add(inputPanel, BorderLayout.NORTH);
        contentPanel.add(proposalsScroll, BorderLayout.CENTER);
        contentPanel.add(logScroll, BorderLayout.SOUTH);

        // Add padding around the main content
        frame.add(contentPanel, BorderLayout.CENTER);
        frame.getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Button actions
        publishButton.addActionListener(e -> {
            if (validateInputs()) {
                publishOffer();
                publishButton.setEnabled(false);
                startButton.setEnabled(true);
                logArea.append("Offer published: " + item + " with minimum price " + minPrice + "\n");
            }
        });

        startButton.addActionListener(e -> {
            if (!auctionInProgress) {
                startAuction();
                startButton.setText("Stop Auction");
                itemField.setEnabled(false);
                priceField.setEnabled(false);
                timeField.setEnabled(false);
            } else {
                stopAuction();
                startButton.setText("Start Auction");
                resetForm();
            }
        });

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    /**
     * Validates the input fields for item, price, and time.
     * @return true if inputs are valid, false otherwise.
     */
    private boolean validateInputs() {
        try {
            item = itemField.getText().trim();
            if (item.isEmpty()) {
                throw new IllegalArgumentException("Item name cannot be empty");
            }
            minPrice = Double.parseDouble(priceField.getText().trim());
            timeLeft = Integer.parseInt(timeField.getText().trim());
            if (minPrice <= 0 || timeLeft <= 0) {
                throw new IllegalArgumentException("Price and time must be positive");
            }
            return true;
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(frame, "Please enter valid numbers for price and time");
            return false;
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(frame, e.getMessage());
            return false;
        }
    }

    /**
     * Publishes the offer to the auctioneer agent.
     */
    private void publishOffer() {
        ACLMessage announce = new ACLMessage(ACLMessage.INFORM);
        announce.addReceiver(new AID("auctioneer", AID.ISLOCALNAME));
        announce.setContent("PUBLISH;" + item + ";" + minPrice);
        send(announce);
    }

    /**
     * Starts the auction process.
     */
    private void startAuction() {
        auctionInProgress = true;
        currentProposals.clear();
        proposalsPanel.removeAll();

        ACLMessage start = new ACLMessage(ACLMessage.INFORM);
        start.addReceiver(new AID("auctioneer", AID.ISLOCALNAME));
        start.setContent("START;" + item + ";" + minPrice + ";" + timeLeft);
        send(start);

        auctionTimer = new Timer(1000, e -> {
            timeLeft--;
            timerLabel.setText("Time left: " + timeLeft + "s");
            if (timeLeft <= 0) {
                stopAuction();
                ((Timer)e.getSource()).stop();
            }
        });
        auctionTimer.start();

        logArea.append("Started auction for " + item + "\n");
    }

    /**
     * Stops the auction process and determines the winner.
     */
    private void stopAuction() {
        auctionInProgress = false;
        if (auctionTimer != null) {
            auctionTimer.stop();
        }

        Optional<Map.Entry<AID, Double>> winner = currentProposals.entrySet()
                .stream()
                .max(Map.Entry.comparingByValue());

        if (winner.isPresent() && winner.get().getValue() >= minPrice) {
            ACLMessage accept = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
            accept.addReceiver(winner.get().getKey());
            accept.setContent(item + ";" + winner.get().getValue());
            send(accept);

            ACLMessage reject = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
            currentProposals.keySet().stream()
                    .filter(aid -> !aid.equals(winner.get().getKey()))
                    .forEach(reject::addReceiver);
            reject.setContent(item);
            send(reject);

            logArea.append("Auction ended. Winner: " + winner.get().getKey().getLocalName() +
                    " with bid: " + winner.get().getValue() + "\n");
        } else {
            logArea.append("Auction ended with no valid bids\n");
        }

        resetForm();
    }

    /**
     * Resets the form to its initial state.
     */
    private void resetForm() {
        itemField.setEnabled(true);
        priceField.setEnabled(true);
        timeField.setEnabled(true);
        publishButton.setEnabled(true);
        startButton.setEnabled(false);
        currentProposals.clear();
        updateProposalsPanel();
        timerLabel.setText("Time left: --");
    }

    /**
     * Updates the proposals panel with the current bids.
     */
    private void updateProposalsPanel() {
        proposalsPanel.removeAll();
        for (Map.Entry<AID, Double> proposal : currentProposals.entrySet()) {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            panel.add(new JLabel(proposal.getKey().getLocalName() + ": " + proposal.getValue() + " MAD"));

            if (auctionInProgress) {
                JButton acceptButton = new JButton("Accept");
                acceptButton.addActionListener(e -> {
                    ACLMessage accept = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                    accept.addReceiver(proposal.getKey());
                    accept.setContent(item + ";" + proposal.getValue());
                    send(accept);
                    stopAuction();
                });
                panel.add(acceptButton);
            }
            proposalsPanel.add(panel);
        }
        proposalsPanel.revalidate();
        proposalsPanel.repaint();
    }
}