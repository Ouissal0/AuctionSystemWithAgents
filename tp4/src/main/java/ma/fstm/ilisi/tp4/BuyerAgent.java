package ma.fstm.ilisi.tp4;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

import javax.swing.*;
import java.awt.*;

/**
 * BuyerAgent is a JADE agent that participates in auctions by placing bids.
 * It handles receiving auction information, placing bids, and requesting more time.
 */
public class BuyerAgent extends Agent {
    private JFrame frame;
    private JTextArea logArea;
    private JTextField bidField;
    private JButton bidButton;
    private JButton moreTimeButton;
    private JLabel statusLabel;
    private JLabel currentMaxBidLabel;
    private double minPrice;
    private double currentMaxBid;
    private String currentItem;
    private boolean canBid = false;

    /**
     * Setup method is called when the agent is initialized.
     * It registers the agent with the Directory Facilitator (DF) and creates the GUI.
     */
    protected void setup() {
        // Register with DF
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("buyer");
        sd.setName("auction-buyer");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        createGUI();

        addBehaviour(new CyclicBehaviour() {
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    switch (msg.getPerformative()) {
                        case ACLMessage.CFP:
                            String[] content = msg.getContent().split(";");
                            currentItem = content[0];
                            minPrice = Double.parseDouble(content[1]);
                            currentMaxBid = minPrice; // Initialize max bid with minimum price
                            canBid = true;
                            statusLabel.setText("Current auction: " + currentItem);
                            currentMaxBidLabel.setText("Current max bid: " + currentMaxBid);
                            bidButton.setEnabled(true);
                            moreTimeButton.setEnabled(true);
                            logArea.append("New auction for " + currentItem + " (min: " + minPrice + ")\n");
                            break;

                        case ACLMessage.ACCEPT_PROPOSAL:
                            logArea.append("Your bid was accepted! You won " + currentItem + "\n");
                            resetBidding();
                            break;

                        case ACLMessage.REJECT_PROPOSAL:
                            logArea.append("Your bid was rejected\n");
                            resetBidding();
                            break;

                        case ACLMessage.INFORM:
                            if (msg.getContent().startsWith("TIME_EXTENDED")) {
                                String[] timeInfo = msg.getContent().split(";");
                                logArea.append("Auction time extended to " + timeInfo[1] + " seconds\n");
                            } else if (msg.getContent().startsWith("NEW_BID")) {
                                // Handle updates about new maximum bids
                                String[] bidInfo = msg.getContent().split(";");
                                currentMaxBid = Double.parseDouble(bidInfo[1]);
                                currentMaxBidLabel.setText("Current max bid: " + currentMaxBid);
                                logArea.append("New maximum bid: " + currentMaxBid + "\n");
                            }
                            break;
                    }
                }
                block();
            }
        });
    }

    /**
     * Resets the bidding state and updates the GUI accordingly.
     */
    private void resetBidding() {
        canBid = false;
        bidButton.setEnabled(false);
        moreTimeButton.setEnabled(false);
        statusLabel.setText("No active auction");
        currentMaxBidLabel.setText("Current max bid: --");
        currentItem = null;
        currentMaxBid = 0;
    }

    /**
     * Creates the GUI for the buyer agent.
     */
    private void createGUI() {
        frame = new JFrame("Buyer: " + getLocalName());
        frame.setLayout(new BorderLayout(10, 10));

        // Status panel
        JPanel statusPanel = new JPanel(new GridLayout(2, 1));
        statusLabel = new JLabel("No active auction");
        currentMaxBidLabel = new JLabel("Current max bid: --");
        statusPanel.add(statusLabel);
        statusPanel.add(currentMaxBidLabel);

        // Bidding panel
        JPanel biddingPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        biddingPanel.setBorder(BorderFactory.createTitledBorder("Place Bid"));

        bidField = new JTextField(10);
        bidButton = new JButton("Submit Bid");
        bidButton.setEnabled(false);
        moreTimeButton = new JButton("Request More Time");
        moreTimeButton.setEnabled(false);

        biddingPanel.add(new JLabel("Amount:"));
        biddingPanel.add(bidField);
        biddingPanel.add(bidButton);
        biddingPanel.add(moreTimeButton);

        // Log panel
        logArea = new JTextArea(10, 40);
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Auction Log"));

        // Main panel
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        mainPanel.add(statusPanel, BorderLayout.NORTH);
        mainPanel.add(biddingPanel, BorderLayout.CENTER);
        mainPanel.add(scrollPane, BorderLayout.SOUTH);

        frame.add(mainPanel);

        // Action listeners
        bidButton.addActionListener(e -> {
            if (canBid) {
                try {
                    double bid = Double.parseDouble(bidField.getText());
                    if (bid > currentMaxBid) {
                        ACLMessage msg = new ACLMessage(ACLMessage.PROPOSE);
                        msg.addReceiver(new AID("seller", AID.ISLOCALNAME));
                        msg.setContent(String.valueOf(bid));
                        send(msg);
                        logArea.append("Bid placed: " + bid + "\n");
                        bidField.setText("");
                    } else {
                        JOptionPane.showMessageDialog(frame,
                                "Bid must be higher than current maximum bid: " + currentMaxBid);
                    }
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(frame,
                            "Please enter a valid number");
                }
            }
        });

        moreTimeButton.addActionListener(e -> {
            if (canBid) {
                ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
                msg.addReceiver(new AID("seller", AID.ISLOCALNAME));
                msg.setContent("MORE_TIME");
                send(msg);
                logArea.append("Requested more time\n");
            }
        });

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    /**
     * Called when the agent is taken down.
     * Deregisters the agent from the DF and disposes of the GUI.
     */
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        frame.dispose();
    }
}