package theWorst;

import arc.Events;
import arc.files.Fi;
import arc.math.Mathf;
import arc.struct.Array;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Timer;
import javafx.util.Pair;
import mindustry.Vars;
import mindustry.core.GameState;
import mindustry.entities.type.Player;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.game.Teams;
import mindustry.io.MapIO;
import mindustry.maps.Map;
import mindustry.ui.dialogs.JoinDialog;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.modules.ItemModule;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageAttachment;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.Event;
import org.javacord.api.event.message.MessageCreateEvent;
import org.json.simple.JSONObject;
import theWorst.dataBase.Database;
import theWorst.dataBase.PlayerData;
import theWorst.dataBase.Rank;
import theWorst.discord.Command;
import theWorst.discord.CommandContext;
import theWorst.discord.DiscordCommands;
import theWorst.discord.MapParser;
import theWorst.helpers.MapManager;
import theWorst.interfaces.LoadSave;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static mindustry.Vars.*;

public class DiscordBot implements LoadSave {
    public static String prefix="!";
    private final int maxMessageLength=2000;

    private static DiscordApi api;

    private boolean setup=false;
    
    private final MapParser mapParser = new MapParser();
    private final DiscordCommands handler = new DiscordCommands();

    private static final HashMap<String, Role> roles = new HashMap<>();
    private static final HashMap<String, TextChannel> channels = new HashMap<>();
    private static Long serverId = null;
    private static final String configFile =Main.directory + "discordSettings.json";

    //public static final HashMap<Long,LinkData> pendingLinks = new HashMap<>();

    /*public static class LinkData{
        public String name,pin,id;

        LinkData(String name,String pin,String id){
            this.name=name;
            this.pin=pin;
            this.id=id;
        }
    }*/

    public DiscordBot() {
        connect();
       /* Events.on(EventType.PlayerJoin.class , e->{
            if(api==null || serverId==null) {
                Log.info("No server id.");
                return;
            }
            PlayerData pd = Database.getData(e.player);
            if(pd==null || pd.trueRank==Rank.griefer) return;
            if(pendingLinks.containsKey(pd.serverId)){
                player.sendMessage("Discord user [orange]"+pendingLinks.get(pd.serverId).name+"[] " +
                        "is trying to link the accounts,if that's you, use \"/link <your pin bot gave you to confirm " +
                        "liking>\"or \"/link refuse\" fi its not you.");
            }
            if(!pd.discordLink.isEmpty()){
                Log.info("Link is here.");
                CompletableFuture<User> optionalUser = api.getUserById(pd.discordLink);
                Timer.schedule(new Timer.Task() {
                    @Override
                    public void run() {
                        if(optionalUser.isDone()){
                            Log.info("user found.");
                            this.cancel();
                            try {
                                User user=optionalUser.get();
                                if(user==null) return;
                                Optional<Server> server = api.getServerById(serverId);
                                if(!server.isPresent()) return;
                                for(Role r:user.getRoles(server.get())){
                                    try{
                                        Rank rank = Rank.valueOf(r.getName());
                                        if(pd.trueRank.getValue()<rank.getValue()){
                                            Database.setRank(pd,rank,e.player);
                                        }

                                    } catch (IllegalArgumentException ignored){}
                                }
                            } catch (InterruptedException | ExecutionException interruptedException) {
                                interruptedException.printStackTrace();
                            }
                        }
                    }
                },0,.1f);
            } else Log.info("Link is not here.");
        });*/
    }

    public static void disconnect(){
        if(api!=null){
            api.disconnect();
            api=null;
        }
    }

    public void connect(){
        setup=false;
        disconnect();
        Tools.loadJson(configFile,(data)-> {
            Tools.JsonMap dataMap = new Tools.JsonMap(data);
            if(data.containsKey("prefix")) prefix =dataMap.getString("prefix");

            try {
                api = new DiscordApiBuilder().setToken(dataMap.getString("token")).login().join();
            } catch (Exception ex){
                Log.info("Could not connect to discord");
                return;
            }

            if(dataMap.containsKey("roles")){
                roles.clear();
                Tools.JsonMap rolesMap = new Tools.JsonMap(dataMap.getJsonObj("roles"));
                for(String o : rolesMap.keys){
                    Optional<Role> role = api.getRoleById(rolesMap.getString(o));
                    if(!role.isPresent()) {
                        Log.info(o+ " role not found.");
                        continue;
                    }
                    if(serverId==null){
                        serverId=role.get().getServer().getId();
                    }
                    roles.put(o,role.get());
                }
            }

            if(dataMap.containsKey("channels")){
                channels.clear();
                Tools.JsonMap channelsMap = new Tools.JsonMap(dataMap.getJsonObj("channels"));
                for(String o : channelsMap.keys){
                    Optional<TextChannel> channel = api.getTextChannelById(channelsMap.getString(o));
                    if(!channel.isPresent()){
                        Log.info(o+ " channel not found.");
                        continue;
                    }
                    channels.put(o,channel.get());
                }
            }

        },this::createDefaultConfig);

        if(api==null) return;


        api.addMessageCreateListener(handler);
        registerCommands(handler);

        api.addMessageCreateListener((event)->{
            if(event.getMessageAuthor().isBotUser()) return;
            if(hasMapAttached(event.getMessage()) && !handler.hasCommand(event.getMessageContent().replace(prefix,""))){
                event.getChannel().sendMessage("If you want to post map use !postmap command!");
                event.getMessage().delete();
            }
        });

        if(channels.containsKey("linked")) {
            TextChannel linkedChannel = channels.get("linked");
            Events.on(EventType.PlayerChatEvent.class,e->{
                if(Tools.isCommandRelated(e.message)) return;
                linkedChannel.sendMessage("**"+Tools.cleanName(e.player.name)+"** : "+e.message.substring(e.message.indexOf("]")+1));
            });

            api.addMessageCreateListener((event)->{
                if(event.getChannel()!=linkedChannel)return;
                if(event.getMessageAuthor().isBotUser()) return;
                if(event.getMessageContent().startsWith(prefix)) return;
                String content=Tools.cleanEmotes(event.getMessageContent());
                if(Tools.isBlank(content)) return;
                Tools.sendChatMessage(event.getMessageAuthor().getName(),"[sky]"+content);
            });
        }

        if(channels.containsKey("commandLog")){
            Events.on(EventType.PlayerChatEvent.class,e->{
                if(!Tools.isCommandRelated(e.message)) return;
                PlayerData pd = Database.getData(e.player);
                channels.get("commandLog").sendMessage(String.format("**%s** - %s (%d): %s",
                        pd.originalName,pd.trueRank.name(),pd.serverId,e.message));
            });
        }
        setup=true;
    }


    public static void onRankChange(String name, long serverId, String prev, String now, String by, String reason) {
        channels.get("log").sendMessage(String.format("**%s** (%d) **%s** -> **%s** \n**by:** %s \n**reason:** %s",
                name,serverId,prev,now,by,reason));
    }

    public static boolean activeLog() {
        return channels.containsKey("log");
    }

    public static boolean isInvalidChannel(MessageCreateEvent event) {
        if(!channels.containsKey("commands")) return false;
        TextChannel commandChannel = channels.get("commands");
        if(event.getChannel().getId()==commandChannel.getId()) return false;
        event.getMessage().delete();
        event.getChannel().sendMessage("This is not channel for commands.");
        return true;
    }

    private void createDefaultConfig() {
        Tools.saveJson(configFile,"Default "+configFile+"was created, edit it to connect your server with discord.",
                ()->{
                    JSONObject data = new JSONObject();
                    data.put("token","Replace this with your bot token.");
                    data.put("prefix","!");
                    JSONObject roles = new JSONObject();
                    roles.put("admin","admin role id");
                    data.put("roles",roles);
                    JSONObject channels = new JSONObject();
                    channels.put("maps","channel where maps will be posted.");
                    channels.put("commands","channel where you use commands, not required.");
                    channels.put("log","Bot will send rank change data here");
                    channels.put("commandLog","bot will send all commands that players used.");
                    channels.put("linked","Bot will link this channel with servers chat so you can communicate Discord <-> mindustry.");
                    data.put("channels",channels);
                    return data;
                });
    }

    private EmbedBuilder formMapEmbed(Map map,String reason,CommandContext ctx) {

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("**"+reason.toUpperCase()+"** "+map.name())
                .setAuthor(map.author())
                .setDescription(map.description()+"\n**Posted by "+ctx.author.getName()+"**");
                try{
                    InputStream in = new FileInputStream(map.file.file());
                    BufferedImage img = mapParser.parseMap(in).image;
                    eb.setImage(img);
                } catch (IOException ex){
                    ctx.reply("I em unable to post map with image.");
                }

        return eb;
    }

    public static boolean hasMapAttached(Message message){
        return message.getAttachments().size() == 1 && message.getAttachments().get(0).getFileName().endsWith(".msav");
    }

    @Override
    public void load(JSONObject data) {
        if(!setup) return;
        for(Object o:data.keySet()){
            String command=(String)o;
            String role=(String)data.get(o);
            Role found=null;
            for(Role r:roles.values()){
                if(r.getName().equals(role)){
                    found=r;
                }
            }
            handler.commands.get(command).role=found;
        }
    }

    @Override
    public JSONObject save() {
        JSONObject data=new JSONObject();
        if(!setup) return data;
        for(String command:handler.commands.keySet()){
            Role role=handler.commands.get(command).role;
            if(role==null) continue;
            data.put(command,role.getName());
        }
        return data;
    }

    private void registerCommands(DiscordCommands handler) {
        Role admin = roles.get("admin");
        handler.registerCommand(new Command("help") {
            {
                description = "Shows all commands and their description.";
            }
            @Override
            public void run(CommandContext ctx) {
                EmbedBuilder eb =new EmbedBuilder()
                        .setTitle("COMMANDS")
                        .setColor(Color.orange);
                StringBuilder sb=new StringBuilder();
                sb.append("*!commandName - restriction - <necessary> [optional] |.fileExtension| - description*\n");
                for(String s:handler.commands.keySet()){
                    sb.append(handler.commands.get(s).getInfo()).append("\n");
                }
                ctx.channel.sendMessage(eb.setDescription(sb.toString()));
            }
        });

       /* handler.registerCommand(new Command("link","<serverId>") {
            {
                description = "Links your discord with your server profile.";
            }
            @Override
            public void run(CommandContext ctx) {
                if(!Strings.canParsePostiveInt(ctx.args[0])){
                    ctx.reply("Server Id has to be integer.");
                    return;
                }
                Optional<User> optionalUser = ctx.author.asUser();
                if(!optionalUser.isPresent()){
                    ctx.reply("It appears that you are not a user.");
                    return;
                }
                User user = optionalUser.get();
                PlayerData pd = Database.getData(Integer.parseInt(ctx.args[0]));
                if(pd==null){
                    ctx.reply("Account not found.");
                    return;
                }
                if(pd.discordLink.equals(user.getIdAsString())){
                    ctx.reply("You already have this account linked.");
                    return;
                }
                String pin =String.valueOf(Mathf.random(1000,9999));
                user.sendMessage("Use /link "+pin+" command in game to confirm the linking.");
                pendingLinks.put(pd.serverId, new LinkData(user.getName(),pin, user.getIdAsString()));
            }
        });*/

        handler.registerCommand(new Command("gamestate") {
            {
                description = "Shows information about current game state.";
            }
            @Override
            public void run(CommandContext ctx) {
                EmbedBuilder eb =new EmbedBuilder().setTitle("GAME STATE");
                if(Vars.state.is(GameState.State.playing)){
                    eb
                            .addField("map", Vars.world.getMap().name())
                            .addField("mode", Vars.state.rules.mode().name())
                            .addInlineField("players",String.valueOf(Vars.playerGroup.size()))
                            .addInlineField("wave",String.valueOf(Vars.state.wave))
                            .addInlineField("enemies",String.valueOf(Vars.state.enemies))
                            .setImage(Tools.getMiniMapImg())
                            .setColor(Color.green);
                } else {
                    eb
                            .setColor(Color.red)
                            .setDescription("Server is not hosting at the moment.");
                }
                ctx.channel.sendMessage(eb);
            }
        });

        handler.registerCommand(new Command("players") {
            {
                description = "Shows list of online players.";
            }
            @Override
            public void run(CommandContext ctx) {
                StringBuilder sb = new StringBuilder();
                for(Player p:Vars.playerGroup){
                    PlayerData pd = Database.getData(p);
                    sb.append(pd.originalName).append(" | ").append(pd.trueRank.name()).append(" | ").append(pd.serverId).append("\n");
                }
                EmbedBuilder eb =new EmbedBuilder()
                        .setTitle("PLAYERS ONLINE")
                        .setColor(Color.green)
                        .setDescription(sb.toString());
                if(Vars.playerGroup.size()==0) eb.setDescription("No players online.");
                ctx.channel.sendMessage(eb);
            }
        });

        handler.registerCommand(new Command("resinfo") {
            {
                description = "Check the amount of resources in the core.";
            }
            public void run(CommandContext ctx) {
                if (!state.rules.waves) {
                    ctx.reply("Only available in survival mode!");
                    return;
                }
                // the normal player team is "sharded"
                Teams.TeamData data = state.teams.get(Team.sharded);
                if(data.cores.isEmpty()){
                    ctx.reply("No cores no resources");
                    return;
                }
                //-- Items are shared between cores
                CoreBlock.CoreEntity core = data.cores.first();
                ItemModule items = core.items;
                EmbedBuilder eb = new EmbedBuilder()
                        .setTitle("CORE RESOURCES");
                items.forEach((item, amount) -> eb.addInlineField(item.name, String.valueOf(amount)));
                ctx.channel.sendMessage(eb);
            }
        });

        handler.registerCommand(new Command("downloadmap","<mapName/id>") {
            {
                description = "Preview and download a server map in a .msav file format.";
            }
            public void run(CommandContext ctx) {

                Map found = MapManager.findMap(ctx.args[0]);

                if (found == null) {
                    ctx.reply("Map not found!");
                    return;
                }

                ctx.channel.sendMessage(formMapEmbed(found,"download",ctx),found.file.file());
            }
        });

        handler.registerCommand(new Command("maps") {
            {
                description = "Shows all server maps and ids.";
            }
            @Override
            public void run(CommandContext ctx) {
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("MAP LIST")
                        .setColor(Color.orange);
                StringBuilder b =new StringBuilder();
                int i=0;
                for(Map map:Vars.maps.customMaps()){
                    double rating= MapManager.getMapRating(map);
                    b.append(i).append(" | ").append(map.name()).append(" | ").append(String.format("%.2f/10",rating)).append("\n");
                    i++;
                }
                embed.setDescription(b.toString());
                ctx.channel.sendMessage(embed);
            }
        });

        handler.registerCommand(new Command("search","<searchKey/chinese/russian/sort/online/rank> [sortType/rankName] [reverse]") {
            {
                description = "Search for player in server database.Be careful database is big so if i resolve huge " +
                        "search result i will send it to you in dm";
            }
            @Override
            public void run(CommandContext ctx) {
                Array<String> res = Tools.getSearchResult(ctx.args, null, ctx.channel);
                if (res == null) return;

                StringBuilder mb = new StringBuilder();
                int shown = 0;
                int begin = Math.max(0,res.size-20);
                for (int i = begin; i <res.size; i++) {
                    String line =Tools.cleanColors(res.get(i));
                    if(mb.length()+line.length()>maxMessageLength) break;
                    shown++;
                    mb.insert(0,Tools.cleanColors(res.get(i))+"\n");
                }
                if (res.isEmpty()) {
                    ctx.reply("No results found.");
                } else {
                    ctx.channel.sendMessage(mb.toString());
                    if(shown!=res.size){
                        ctx.reply("I em showing just "+shown+" out of "+res.size+".");
                    }
                }
            }
        });

        handler.registerCommand(new Command("info","<name/id>") {
            {
                description = "Shows info about player.";
            }
            @Override
            public void run(CommandContext ctx) {
                PlayerData pd = Database.findData(ctx.args[0]);
                if(pd==null){
                    ctx.reply("No data found.");
                    return;
                }
                String data = Tools.cleanColors(pd.toString()).replace("==PLayer data==\n\n","");
                ctx.channel.sendMessage(new EmbedBuilder().setDescription(data).setTitle("PLAYER INFO").setColor(Color.blue));
            }
        });

        handler.registerCommand(new Command("postmap","|.msav|") {
            @Override
            public void run(CommandContext ctx) {
                Message message = ctx.event.getMessage();
                MessageAttachment a = message.getAttachments().get(0);

                String dir =Main.directory+"postedMaps/";
                new File(dir).mkdir();
                try {
                    String path = dir+a.getFileName();
                    Tools.downloadFile(a.downloadAsInputStream(),path);
                    Fi mapFile = new Fi(path);
                    Map posted = MapIO.createMap(mapFile,true);

                    EmbedBuilder eb = formMapEmbed(posted,"map post",ctx);

                    if(channels.containsKey("maps")){
                        channels.get("maps").sendMessage(eb,mapFile.file());
                        ctx.reply("Map posted.");
                    }else {
                        ctx.channel.sendMessage(eb,mapFile.file());
                    }
                } catch (IOException ex){
                    ctx.reply("I em unable to post your map.");
                }
            }
        });

        handler.registerCommand(new Command("restrict","<command> <role/remove>") {
            {
                description = "Sets role restriction for command.";
                role = admin;
            }
            @Override
            public void run(CommandContext ctx) {
                if(!handler.hasCommand(ctx.args[0]) ){
                    String match = Tools.findBestMatch(ctx.args[0],handler.commands.keySet());
                    ctx.reply("Sorry i don t know this command.");
                    if(match==null) return;
                    ctx.reply("Did you mean "+match+"?");
                    return;
                }
                if(ctx.args[1].equals("remove")){
                    handler.commands.get(ctx.args[0]).role=null;
                    ctx.reply(String.format("Restriction from %s wos removed.", ctx.args[0]));
                }
                if(!roles.containsKey(ctx.args[1])){
                    ctx.reply("It might be little confusing but role names match names in the config file.\n"
                            +roles.keySet().toString());
                    return;
                }
                handler.commands.get(ctx.args[0]).role=roles.get(ctx.args[1]);

                ctx.reply(String.format("Role of %s is now %s.", ctx.args[0], ctx.args[1]));
            }
        });

        handler.registerCommand(new Command("addmap","|.msav|") {
            {
                description = "Adds map to server.";
                role=admin;
            }
            @Override
            public void run(CommandContext ctx) {
                Message message = ctx.event.getMessage();
                MessageAttachment a = message.getAttachments().get(0);
                try {
                    String path="config/maps/"+a.getFileName();
                    Tools.downloadFile(a.downloadAsInputStream(),path);
                    Fi mapFile = new Fi(path);
                    Map added = MapIO.createMap(mapFile,true);

                    EmbedBuilder eb = formMapEmbed(added,"new map",ctx);

                    if(channels.containsKey("maps")){
                        channels.get("maps").sendMessage(eb,mapFile.file());
                        ctx.reply("Map added.");
                    }else {
                        ctx.channel.sendMessage(eb,mapFile.file());
                    }

                    maps.reload();
                } catch (IOException ex){
                    ctx.reply("I em unable to upload map.");
                    ex.printStackTrace();
                }

            }
        });

        handler.registerCommand(new Command("removemap","<name/id>") {
            {
                description = "Removes map from server.";
                role=admin;
            }
            @Override
            public void run(CommandContext ctx) {
                Map removed = MapManager.findMap(ctx.args[0]);

                if(removed==null){
                    ctx.reply("Map not found.");
                    return;
                }

                EmbedBuilder eb = formMapEmbed(removed,"removed map",ctx);
                CompletableFuture<Message> mess;
                if(channels.containsKey("maps")){
                    mess =  channels.get("maps").sendMessage(eb,removed.file.file());
                    ctx.reply("Map removed.");
                }else {
                    mess = ctx.channel.sendMessage(eb,removed.file.file());
                }
               Timer.schedule(new Timer.Task() {
                   @Override
                   public void run() {
                       if(mess.isDone()){
                           maps.removeMap(removed);
                           maps.reload();
                           this.cancel();
                       }
                   }
               },0,1);
            }
        });

        handler.registerCommand(new Command("emergency","[off]") {
            {
                description = "Initialises or terminates emergency, available just for admins.";
                role = admin;
            }
            @Override
            public void run(CommandContext ctx) {
                ActionManager.switchEmergency(ctx.args.length==1);
                if(ActionManager.isEmergency()){
                    ctx.reply("Emergency started.");
                } else {
                    ctx.reply("Emergency stopped.");
                }
            }
        });
        
        handler.registerCommand(new Command("setrank","<name/id> <rank> [reason...]") {
            {
                description = "Sets rank of the player, available just for admins.";
                role = admin;
            }
            @Override
            public void run(CommandContext ctx) {
                Player player = new Player();
                player.name=ctx.author.getName();
                switch (Database.setRankViaCommand(player,ctx.args[0],ctx.args[1],ctx.args.length==3 ? ctx.args[2] : null)){
                    case notFound:
                        ctx.reply("Player not found.");
                        break;
                    case notPermitted:
                        ctx.reply("Changing or assigning admin rank can be done only thorough terminal.");
                        break;
                    case invalidRank:
                        ctx.reply("Rank not found.\nRanks:" + Arrays.toString(Rank.values())+"\n" +
                                "Custom ranks:"+Database.ranks.keySet());
                        break;
                    case success:
                        ctx.reply("Rank successfully changed.");
                }
            }
        });
    }


}
