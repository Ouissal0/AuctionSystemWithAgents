package ma.fstm.ilisi.tp4;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;

/**
 * The Main class is the entry point of the application.
 * It sets up the JADE runtime environment and creates the main container.
 * It also creates and starts the seller, auctioneer, and buyer agents.
 */
public class Main {
    public static void main(String[] args) {
        // Get the JADE runtime instance
        Runtime rt = Runtime.instance();

        // Create a profile for the main container
        Profile p = new ProfileImpl();
        p.setParameter(Profile.MAIN_HOST, "localhost");
        p.setParameter(Profile.GUI, "true");

        // Create the main container
        AgentContainer container = rt.createMainContainer(p);

        try {
            // Create and start the seller agent
            container.createNewAgent("seller", "ma.fstm.ilisi.tp4.SellerAgent", null).start();
            // Create and start the auctioneer agent
            container.createNewAgent("auctioneer", "ma.fstm.ilisi.tp4.AuctioneerAgent", null).start();

            // Create and start four buyer agents
            for (int i = 0; i < 4; i++) {
                container.createNewAgent("buyer" + (i+1), "ma.fstm.ilisi.tp4.BuyerAgent", null).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}