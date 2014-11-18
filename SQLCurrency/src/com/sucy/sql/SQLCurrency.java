package com.sucy.sql;

import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import net.milkbowl.vault.permission.Permission;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitRunnable;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.rit.sucy.sql.ColumnType;
import com.rit.sucy.sql.direct.SQLEntry;
import com.rit.sucy.sql.direct.SQLTable;

/**
 * <p>A simple plugin that manages player currency balances over a server network
 * using a MySQL database.</p>
 */
public class SQLCurrency extends JavaPlugin implements Listener,PluginMessageListener {
	public static Permission permission = null;
    private enum Action { ADD, SUBTRACT, SET };

    private final HashMap<UUID, Integer> funds = new HashMap<UUID, Integer>();
    private final HashMap<UUID, Integer> credits = new HashMap<UUID, Integer>();
    private SQLTable table;
    private int defaultMoney = 1250;
    private int playerCount = 0;
    private List<Player> onlinePlayers = new ArrayList<Player>();
	final String DRIVER = "com.mysql.jdbc.Driver";
	final String DATABASE = "gamerwu85_site";
	final String CONNECTION = "jdbc:mysql://159.253.0.127/" + DATABASE;

	final String USERNAME = "gamerwu85_dedi1";
	
	final String PASSWORD = "TxuXi44x";
	
    /**
     * <p>Enables the plugin, setting up the MySQL connections.</p>
     * <p>This should not ever be called by other plugins.</p>
     */
    @SuppressWarnings("deprecation")
	@Override
    public void onEnable() {
    	setupPermissions();
        getServer().getPluginManager().registerEvents(this, this);
	    this.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
	    this.getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", this);
	    
        table = SQLManager.getTable(this, "players");
        table.createColumn("username", ColumnType.STRING_32);
        table.createColumn("balance", ColumnType.INT);
        table.createColumn("credits", ColumnType.INT);

        // Update the balance of online players
        for (Player player : getServer().getOnlinePlayers()) {
            new UpdateTask(player.getName(), player.getUniqueId()).runTaskAsynchronously(this);
        }

        //run repeating task to tell everyone in list that they are already registered
    }
    public void setupPermissions(){
    	RegisteredServiceProvider<Permission> permissionProvider = getServer().getServicesManager().getRegistration(net.milkbowl.vault.permission.Permission.class);
        if (permissionProvider != null) {
            permission = permissionProvider.getProvider();
        }
    }
	public void vote(Player p){
		new VoteTask(p).runTaskAsynchronously(this);
	}
    /**
     * <p>Clears up event handlers before exiting.</p>
     */
    @Override
    public void onDisable() {
        HandlerList.unregisterAll((JavaPlugin)this);
    }
    public static boolean isPremium(Player p){
    	if(permission.has(p, "premium.normal"))
    		return true;
    	return false;
    }
    public static boolean isPremiumPlus(Player p){
    	if(permission.has(p, "premium.plus"))
    		return true;
    	return false;
    }
    public static  void addPremium(Player p){
    	permission.playerAdd(p, "premium.normal");
    }
    public static void addPremiumPlus(Player p){
    	permission.playerAdd(p, "premium.plus");
    }
    /**
     * <p>Loads a player's balance when they join the server.</p>
     *
     * @param event event details
     */
    @EventHandler (priority = EventPriority.MONITOR)
    public void onJoin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            new UpdateTask(event.getName(), event.getUniqueId()).run();
        }
    }
    public void registerPlayer(Player p, String email, String password) throws IllegalArgumentException, IllegalStateException, UnsupportedEncodingException{
    	
    	new RegisterTask(p,email,password).runTaskAsynchronously(this);
    }
    /**
     * <p>Adds a specified amount to a player's balance.</p>
     *
     * @param player player to add to
     * @param amount amount to add to their balance
     */
    public void add(Player player, int amount) {
        if (-amount > get(player)) throw new IllegalArgumentException("The player does not have that much funds");
        funds.put(player.getUniqueId(), funds.get(player.getUniqueId()) + amount);
        new ModifyTask(player.getUniqueId().toString(), amount, Action.ADD).runTaskAsynchronously(this);
        getServer().getPluginManager().callEvent(new CurrencyUpdateEvent(player, get(player)));
    }
    public void addPointsOffline(String id,int amount){
    	new ModifyTask(id, amount, Action.ADD).runTaskAsynchronously(this);
    }
    public void addCreditsOffline(String id,int amount){
    	new ModifyTask(id, amount, Action.ADD).runTaskAsynchronously(this);
    }
    public void addCredits(Player player, int amount) {
        if (-amount > get(player)) throw new IllegalArgumentException("The player does not have that much funds");
        credits.put(player.getUniqueId(), credits.get(player.getUniqueId()) + amount);
        new ModifyCreditsTask(player.getUniqueId().toString(), amount, Action.ADD).runTaskAsynchronously(this);
        getServer().getPluginManager().callEvent(new CurrencyUpdateEvent(player, get(player)));
    }

    /**
     * <p>Subtracts a specified amount from a player's balance.</p>
     *
     * @param player player to subtract from
     * @param amount amount to subtract from their balance
     */
    public void subtract(Player player, int amount) {
        if (amount > get(player)) throw new IllegalArgumentException("The player does not have that much funds");
        funds.put(player.getUniqueId(), funds.get(player.getUniqueId()) - amount);
        new ModifyTask(player.getUniqueId().toString(), amount, Action.SUBTRACT).runTaskAsynchronously(this);
        getServer().getPluginManager().callEvent(new CurrencyUpdateEvent(player, get(player)));
    }
    public void subtractCredits(Player player, int amount) {
        if (amount > get(player)) throw new IllegalArgumentException("The player does not have that much funds");
        credits.put(player.getUniqueId(), credits.get(player.getUniqueId()) - amount);
        new ModifyCreditsTask(player.getUniqueId().toString(), amount, Action.SUBTRACT).runTaskAsynchronously(this);
        getServer().getPluginManager().callEvent(new CurrencyUpdateEvent(player, get(player)));
    }

    /**
     * <p>Sets the player's balance to a specified amount.</p>
     *
     * @param player player to set the balance for
     * @param amount amount to set the balance to
     */
    public void set(Player player, int amount) {
        if (amount < 0) throw new IllegalArgumentException("You cannot set a player's balance to below 0.");
        funds.put(player.getUniqueId(), amount);
        new ModifyTask(player.getUniqueId().toString(), amount, Action.SET).runTaskAsynchronously(this);
        getServer().getPluginManager().callEvent(new CurrencyUpdateEvent(player, amount));
    }
    public void setCredits(Player player, int amount) {
        if (amount < 0) throw new IllegalArgumentException("You cannot set a player's balance to below 0.");
        credits.put(player.getUniqueId(), amount);
        new ModifyCreditsTask(player.getUniqueId().toString(), amount, Action.SET).runTaskAsynchronously(this);
        getServer().getPluginManager().callEvent(new CurrencyUpdateEvent(player, amount));
    }

    /**
     * <p>Retrieves the current balance of a player.</p>
     *
     * @param player player to retrieve the balance of
     * @return       the current balance of the player
     */
    public int get(Player player) {
        return funds.get(player.getUniqueId());
    }
    public int getCredits(Player player) {
        return credits.get(player.getUniqueId());
    }

    private class ModifyTask extends BukkitRunnable {
        private Action action;
        private String id;
        private int amount;
        public ModifyTask(String id, int amount, Action action) {
            this.id = id;
            this.amount = amount;
            this.action = action;
        }
        @Override
        public void run() {
            SQLEntry entry = table.createEntry(id);
            if (action == Action.SET) {
                entry.set("balance", amount);
            }
            else {
                int current = Math.max(entry.getInt("balance"), 0);
                if (action == Action.ADD) current += amount;
                else if (action == Action.SUBTRACT) current -= amount;
                entry.set("balance", current);
            }
        }
    }
    private class ModifyCreditsTask extends BukkitRunnable {
        private Action action;
        private String id;
        private int amount;
        public ModifyCreditsTask(String id, int amount, Action action) {
            this.id = id;
            this.amount = amount;
            this.action = action;
        }
        @Override
        public void run() {
            SQLEntry entry = table.createEntry(id);
            if (action == Action.SET) {
                entry.set("credits", amount);
            }
            else {
                int current = Math.max(entry.getInt("credits"), 0);
                if (action == Action.ADD) current += amount;
                else if (action == Action.SUBTRACT) current -= amount;
                entry.set("credits", current);
            }
        }
    }
    private class RegisterTask extends BukkitRunnable {
        private Player p;
        private String email;
        private String password;
        private String salt;
       
        public RegisterTask(Player p, String email, String password) throws UnsupportedEncodingException {
            this.p = p;
            this.email = email;
            //Random r = new SecureRandom();
           // byte[] saltInBytes = new byte[16];
          //  p.sendMessage("SaltInBytes: " + saltInBytes);
           // r.nextBytes(saltInBytes);
            String salt = randomString(16);
            p.sendMessage("salt: " + salt);
            this.salt = Md5.getHash(salt);
            this.password = Md5.getHash(password + salt);
            
        }
        String randomString(final int length) {
            Random r = new SecureRandom(); // perhaps make it a class variable so you don't make a new one every time
            StringBuilder sb = new StringBuilder();
            for(int i = 0; i < length; i++) {
                char c = (char)((r.nextInt(128-32))+32);
                sb.append(c);
            }
            return sb.toString();
        }
        @Override
        public void run() {
        	if(!email.contains("@") || !email.contains(".")){
        		p.sendMessage(ChatColor.RED + "Your email is bad formatted");
        		return;
        	}else if(email.length() <= 7){
        		p.sendMessage(ChatColor.RED + "Your email needs to be longer then 7 characters");
        		return;
        	}else if(password.length() <= 8){
        		p.sendMessage(ChatColor.RED + "Your password needs to be longer then 8 characters");
        		return;
        	}
        	try {
        		Class.forName("com.mysql.jdbc.Driver");
    			Connection con = DriverManager.getConnection(CONNECTION +
                        "?user="+ USERNAME+"&password=" + PASSWORD);
    			Statement stmt = con.createStatement();
    			String q = "SELECT * FROM `members` WHERE `email`=" + "'" +email +"'";
    			ResultSet rs = stmt.executeQuery(q);
    			Statement stmt2 = con.createStatement();
    			String q2 = "SELECT * FROM `members` WHERE `uuid`=" + "'" +p.getUniqueId() +"'";
    			ResultSet rs2 = stmt2.executeQuery(q2);
        		if(rs.next()){
        			p.sendMessage(ChatColor.AQUA + "This e-mail address is already in use.");
        		}else if(rs2.next()){
        			p.sendMessage(ChatColor.AQUA + "You already registered an account.");
        		}else{
    			Statement statement = con.createStatement();
    			String querry = "INSERT INTO `members`(`uuid`, `username`, `password`, `email`, `money`,`salt`) VALUES ('" + p.getUniqueId() + "','" + p.getName() + "','" +password+"','"+email +"','"+ get(p)+"','"+ salt +"')";
    			statement.executeUpdate(querry);
    			p.sendMessage(ChatColor.AQUA + "Account registered. Your e-mail address is " + ChatColor.YELLOW + email + ChatColor.AQUA + ".");
    			rs.close();
    			rs2.close();
    			stmt.close();
    			stmt2.close();
    			statement.close();
    			con.close();
        		}
    		} catch (SQLException e) {
    			e.printStackTrace();
    		} catch (ClassNotFoundException e) {
    			e.printStackTrace();
    		}
        }
    }
    class VoteTask extends BukkitRunnable {
    	Player p;
    	public VoteTask(Player p) {
    		this.p = p;

    	}

    	@Override
    	public void run() {

			Connection con;
			try {
	    		Class.forName(DRIVER);
				con = DriverManager.getConnection(CONNECTION,USERNAME,PASSWORD);
				Statement stmt = con.createStatement();
				String q = "SELECT * FROM `members` WHERE `username`=" + "'" +p.getName() +"'";
				ResultSet rs = stmt.executeQuery(q);
				if(rs.next()){
					Statement stmt2 = con.createStatement();
					q = "UPDATE `members` SET `voteCount` = `voteCount` + 1";
					stmt2.executeUpdate(q);
					Statement stmt3 = con.createStatement();
					q = "SELECT `voteCount` FROM `members` WHERE `username`=" + "'" +p.getName() +"'";
					ResultSet rs3 = stmt3.executeQuery(q);
					if(rs3.next()){
						p.sendMessage( ChatColor.AQUA + "Thanks for voting towards the network! You now have " + ChatColor.YELLOW + rs3.getString("voteCount") + ".00 voting points" +ChatColor.AQUA + ".");
					}
					rs3.close();
					stmt3.close();
					stmt2.close();
				}else{
					p.sendMessage( ChatColor.AQUA + "You need an account to earn voting points. Please do /register <email> <password>");
				}
				rs.close();
				stmt.close();
				con.close();
			} catch (SQLException | ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			

    	}

    }
    private class UpdateTask extends BukkitRunnable {
        private String name;
        private UUID id;
        public UpdateTask(String name, UUID id) {
            this.id = id;
            this.name = name;
        }
        public void run() {
            SQLEntry entry = table.createEntry(id.toString());
            entry.set("username", name);
            int balance = entry.getInt("balance");
            int balanceC = entry.getInt("credits");
            balance = balance == -1 ? defaultMoney : balance;
            balanceC = balanceC == -1 ? defaultMoney : balanceC;
            funds.put(id, balance);
            credits.put(id, balanceC);
            entry.set("balance", balance);
            entry.set("credits",balanceC);
        }
    }
	@Override
	  public void onPluginMessageReceived(String channel, Player player, byte[] message) {
	    if (!channel.equals("BungeeCord")) {
	      return;
	    }
	    ByteArrayDataInput in = ByteStreams.newDataInput(message);
	    String subchannel = in.readUTF();
	    if (subchannel.equals("PlayerCount")) {
	    	in.readUTF();
	    	int playercount = in.readInt();
	    	this.playerCount = playercount;
	      // Use the code sample in the 'Response' sections below to read
	      // the data.
	    	
	    }
	  }
	private int getPlayers(){
		return playerCount;
	}
	class PlayerCountTask extends BukkitRunnable{
		Plugin plugin;
		public PlayerCountTask(Plugin plugin){
			this.plugin = plugin;
		}
		@Override
		public void run() {
			ByteArrayDataOutput out = ByteStreams.newDataOutput();
			out.writeUTF("PlayerCount");
			out.writeUTF("ALL");
			Bukkit.getServer().sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
		}
		
	}
}
