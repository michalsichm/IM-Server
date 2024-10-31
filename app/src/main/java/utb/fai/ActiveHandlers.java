package utb.fai;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ActiveHandlers {
    private static final long serialVersionUID = 1L;
    private ConcurrentHashMap<String, SocketHandler> activeHandlersMap = new ConcurrentHashMap<String, SocketHandler>();
    // private List<String> clientNames = Collections.synchronizedList(new
    // ArrayList<>());
    private List<String> groups = Collections.synchronizedList(new ArrayList<>());

    /**
     * sendMessageToAll - Pole zprávu vem aktivním klientùm kromì sebe sama
     * 
     * @param sender  - reference odesílatele
     * @param message - øetìzec se zprávou
     */
    synchronized void sendMessageToAllInCurrentGroup(SocketHandler sender, String message) {
        // if (message.startsWith("#")) return;
        for (SocketHandler handler : activeHandlersMap.values()) {// pro vechny aktivní handlery
            if (!handler.groups.contains(sender.currentGroup) || handler == sender)
                continue;
            if (!handler.messages.offer(message)) // zkus pøidat zprávu do fronty jeho zpráv
                System.err.printf("Client %s message queue is full, dropping the message!\n", handler.clientName);
        }
    }

    /**
     * add pøidá do mnoiny aktivních handlerù nový handler.
     * Metoda je sychronizovaná, protoe HashSet neumí multithreading.
     * 
     * @param handler - reference na handler, který se má pøidat.
     * @return true if the set did not already contain the specified element.
     */
    synchronized void add(SocketHandler handler) {
        activeHandlersMap.put(handler.clientName, handler);
        // return activeHandlersMap.put(handler.clientName, handler);
    }

    /**
     * remove odebere z mnoiny aktivních handlerù nový handler.
     * Metoda je sychronizovaná, protoe HashSet neumí multithreading.
     * 
     * @param handler - reference na handler, který se má odstranit
     * @return true if the set did not already contain the specified element.
     */
    synchronized void remove(SocketHandler handler) {
        activeHandlersMap.remove(handler.clientName);
    }

    void setMyName(SocketHandler handler, String[] args) throws IOException {
        if (args.length != 2) {
            sendMessage(handler, "Invalid #setMyName command format. Plese use format: #setMyName <name>\n");
            return;
        }

        String newName = args[1];
        if (activeHandlersMap.put(newName, handler) != null) {
            sendMessage(handler, "This name is already in use. Please choose other name\n");
            return;
        }

        for (SocketHandler activeHandler : activeHandlersMap.values()) {
            if (activeHandler == handler) {
                activeHandlersMap.remove(activeHandler.clientName);
                activeHandler.clientName = newName;
                activeHandlersMap.put(handler.clientName, handler);
                sendMessage(handler, "Your name's been changed to " + newName + "\n");
                return;
            }
        }

    }

    void sendPrivate(SocketHandler handler, String[] args) throws IOException {
        if (args.length < 3) {
            sendMessage(handler,
                    "Invalid #sendPrivate command format. Plese use format: #sendPrivate <name> <message>\n");
            return;
        }
        String name = args[1];
        String message  = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        String final_message = "[" + handler.clientName + "]" + " >> " + message;
        SocketHandler receiver = activeHandlersMap.get(name);
        if (receiver == null) {
            sendMessage(handler, "This user doesn't exist\n");
            return;
        }
        receiver.messages.offer(final_message);
    }

    void join(SocketHandler handler, String[] args) throws IOException {
        if (args.length != 2) {
            sendMessage(handler, "Invalid #join command format. Plese use format: #join <room>\n");
            return;
        }

        String groupName = args[1];

        if (handler.groups.contains(groupName)) {
            handler.currentGroup = groupName;
            sendMessage(handler, "Switched to group " + groupName + "\n");
            return;
        }

        if (groups.contains(groupName)) {
            handler.groups.add(groupName);
            handler.currentGroup = groupName;
            sendMessage(handler,
                    "You've been added to group " + groupName + ". Now you can start messaging people in this group\n");
        }

        else {
            groups.add(groupName);
            handler.groups.add(groupName);
            handler.currentGroup = groupName;
            sendMessage(handler,
                    "You've been added to group " + groupName + ". Now you can start messaging people in this group\n");
        }
    }

    void leave(SocketHandler handler, String[] args) throws IOException {
        if (args.length != 2) {
            sendMessage(handler, "Invalid #leave command format. Plese use format: #leave <group_name>\n");
            return;
        }
        String groupName = args[1];
        if (!groups.contains(groupName) && !groupName.equals("public")) {
            sendMessage(handler, "This group doesn't exist!\n");
            return;
        }

        for (SocketHandler activeHandler : activeHandlersMap.values()) {
            if (activeHandler == handler) {
                if (!handler.groups.contains(groupName)) {
                    sendMessage(handler, "You're not in this group!\n");
                    return;
                }
                handler.groups.remove(groupName);
                sendMessage(handler, "Successfully removed from the group " + groupName + ".\n");
                return;
            }
        }

    }

    void groups(SocketHandler handler) throws IOException {
        String groups = String.join(", ", handler.groups);
        sendMessage(handler, "Your groups: " + groups + "\n");
    }

    String askForUserName(SocketHandler handler)
            throws UnsupportedEncodingException, IOException {
        String name = "";
        while (true) {
            sendMessage(handler, "Enter your name without spaces:\n");
            // handler.messages.offer("Enter your name without spaces:\n");
            name = handler.reader.readLine();
            name = name.substring(0, 1).toUpperCase() +
                    name.substring(1).toLowerCase();
            if (name.isBlank() || name.contains(" ")) {
                sendMessage(handler, "Invalid name\n");
                // handler.messages.offer("Invalid name\n");
            } else if (activeHandlersMap.containsKey(name)) {
                sendMessage(handler, "This name is already in use. Plese choose other name\n");
            } else {
                break;
            }
        }

        handler.groups.add("public");
        handler.currentGroup = "public";
        return name;
    }

    void executeCommand(SocketHandler handler, String request) throws IOException {
        String[] args = request.trim().split("\\s+");
        String command = args[0];
        switch (command.toLowerCase()) {
            case "#setmyname":
                setMyName(handler, args);
                break;
            case "#groups":
                groups(handler);
                break;
            case "#join":
                join(handler, args);
                break;
            case "#leave":
                leave(handler, args);
                break;
            case "#sendprivate":
                sendPrivate(handler, args);
                break;
            default:
                sendMessage(handler, "Invalid command\n");
                break;
        }
    }

    private void sendMessage(SocketHandler handler, String message) throws IOException {
        handler.writer.write(message);
        handler.writer.flush();
    }
}
