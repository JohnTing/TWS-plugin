package theWorst.discord;

import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.listener.message.MessageCreateListener;
import theWorst.BotThread;
import theWorst.Main;

import java.awt.*;
import java.util.Arrays;
import java.util.HashMap;



public class DiscordCommands implements MessageCreateListener {
    public HashMap<String,Command> commands = new HashMap<>();

    public void registerCommand(Command c){
        commands.put(c.name,c);
    }


    @Override
    public void onMessageCreate(MessageCreateEvent messageCreateEvent) {
        String message = messageCreateEvent.getMessageContent();
        if(messageCreateEvent.getMessage().getAuthor().isBotUser()) return;
        if(!message.startsWith(BotThread.prefix)) return;
        int nameLength = message.indexOf(" ");
        if(nameLength<0){
            String name = message.substring(BotThread.prefix.length());
            runCommand(name,new CommandContext(messageCreateEvent, new String[0],""));
            return;
        }
        String theMessage = message.substring(nameLength+1);
        String[] args = theMessage.split(" ");
        String name = message.substring(BotThread.prefix.length(),nameLength);
        runCommand(name,new CommandContext(messageCreateEvent,args,theMessage));
    }

    private void runCommand(String name, CommandContext ctx) {
        Command command=commands.get(name);
        if(command==null) return;
        if(!command.hasPerm(ctx)){
            EmbedBuilder msg= new EmbedBuilder()
                    .setColor(Color.red)
                    .setTitle("ACCESS DENIED!")
                    .setDescription("You don't have high enough permission to use this command.");
            ctx.channel.sendMessage(msg);
        } else if(ctx.args.length<command.minArgs || ctx.args.length>command.maxArgs){
            EmbedBuilder msg= new EmbedBuilder()
                    .setColor(Color.red)
                    .setTitle(ctx.args.length<command.minArgs ? "TOO FEW ARGUMENTS!" : "TOO MATCH ARGUMENTS!")
                    .setDescription("Valid format : " + BotThread.prefix + name + " " + command.argStruct );
            ctx.channel.sendMessage(msg);
        } else {
            command.run(ctx);
        }

    }
}
