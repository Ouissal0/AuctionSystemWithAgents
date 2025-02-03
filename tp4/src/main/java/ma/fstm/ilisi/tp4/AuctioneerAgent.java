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
import java.util.*;
import java.util.stream.Collectors;

/**
 * AuctioneerAgent is a JADE agent that manages an auction process.
 * It handles the publication of new offers, starts auctions, and processes bids from buyers.
 */
public class AuctioneerAgent extends Agent {
    private JFrame frame;
    private JTextArea logArea;
    private String currentItem;
    private double minPrice;
    private double currentMaxBid;
    private List<AID> buyers = new ArrayList<>();
    private AID seller;
    private boolean auctionActive = false;
    private final Map<AID, Double> bids = new HashMap<>();

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
                    switch (msg.getPerformative()) {
                        case ACLMessage.INFORM:
                            String[] content = msg.getContent().split(";");
                            if (content[0].equals("PUBLISH")) {
                                // Handle publication of new offer
                                currentItem = content[1];
                                minPrice = Double.parseDouble(content[2]);
                                currentMaxBid = minPrice; // Initialize max bid
                                seller = msg.getSender();
                                logArea.append("New offer published: " + currentItem + " at " + minPrice + "\n");
                            } else if (content[0].equals("START")) {
                                // Start the auction
                                startAuction(content[1], Double.parseDouble(content[2]));
                            }
                            break;

                        case ACLMessage.PROPOSE:
                            if (auctionActive) {
                                double proposedBid = Double.parseDouble(msg.getContent());
                                handleBid(msg.getSender(), proposedBid);
                            }
                            break;
                    }
                }
                block();
            }
        });
    }

    /**
     * Handles a bid from a buyer.
     * @param bidder The AID of the bidder.
     * @param proposedBid The amount of the bid.
     */
    private void handleBid(AID bidder, double proposedBid) {
        if (proposedBid > currentMaxBid) {
            // Accept and broadcast new max bid
            currentMaxBid = proposedBid;
            bids.put(bidder, proposedBid);

            // Forward the bid to seller
            ACLMessage forwardBid = new ACLMessage(ACLMessage.PROPOSE);
            forwardBid.addReceiver(seller);
            forwardBid.setContent(String.valueOf(proposedBid));
            forwardBid.setReplyWith("bid-" + System.currentTimeMillis());
            send(forwardBid);

            // Broadcast new max bid to all buyers
            broadcastMaxBid();

            logArea.append("New highest bid: " + proposedBid + " from " + bidder.getLocalName() + "\n");
        } else {
            // Reject bid
            ACLMessage reject = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
            reject.addReceiver(bidder);
            reject.setContent("Bid too low. Current maximum: " + currentMaxBid);
            send(reject);
            logArea.append("Rejected bid " + proposedBid + " from " + bidder.getLocalName() + " (below maximum)\n");
        }
    }

    /**
     * Broadcasts the current maximum bid to all buyers.
     */
    private void broadcastMaxBid() {
        ACLMessage broadcast = new ACLMessage(ACLMessage.INFORM);
        buyers.forEach(broadcast::addReceiver);
        broadcast.setContent("MAX_BID;" + currentMaxBid);
        send(broadcast);
        logArea.append("Broadcasting current maximum bid: " + currentMaxBid + "\n");
    }

    /**
     * Starts an auction for a given item at a specified price.
     * @param item The item to be auctioned.
     * @param price The starting price of the auction.
     */
    private void startAuction(String item, double price) {
        auctionActive = true;
        currentMaxBid = price;
        bids.clear();
        logArea.append("Starting auction for " + item + "\n");

        // Find buyers
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("buyer");
        template.addServices(sd);

        try {
            DFAgentDescription[] result = DFService.search(this, template);
            buyers = Arrays.stream(result)
                    .map(DFAgentDescription::getName)
                    .collect(Collectors.toList());

            if (!buyers.isEmpty()) {
                // Send CFP to all buyers with initial price
                ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                buyers.forEach(cfp::addReceiver);
                cfp.setContent(item + ";" + price);
                send(cfp);
                logArea.append("Sent CFP to " + buyers.size() + " buyers\n");

                // Initial broadcast of minimum price as current max bid
                broadcastMaxBid();
            } else {
                logArea.append("No buyers found\n");
            }
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
    }

    /**
     * Creates the GUI for the auctioneer agent.
     */
    private void createGUI() {
        frame = new JFrame("Auctioneer: " + getLocalName());

        logArea = new JTextArea(15, 40);
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Auction Log"));

        frame.add(scrollPane);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    /**
     * Called when the agent is taken down.
     * Disposes of the GUI.
     */
    protected void takeDown() {
        frame.dispose();
    }
}