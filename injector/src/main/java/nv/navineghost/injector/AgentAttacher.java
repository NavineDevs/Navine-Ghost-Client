package nv.navineghost.injector;

public final class AgentAttacher {
    private AgentAttacher() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: AgentAttacher <pid> <agentJar> [agentArgs]");
            System.exit(2);
        }
        String pid = args[0].trim();
        String agentJar = args[1].trim();
        String agentArgs = args.length > 2 ? args[2] : "";

        Class<?> vmClass;
        try {
            vmClass = Class.forName("com.sun.tools.attach.VirtualMachine");
        } catch (ClassNotFoundException missing) {
            System.err.println("[ghost-attach] jdk.attach missing on this Java runtime.");
            System.err.println("[ghost-attach] Install a full JDK (JAVA_HOME) or use the DLL inject path.");
            System.err.println("[ghost-attach] java.home=" + System.getProperty("java.home"));
            System.exit(3);
            return;
        }

        Object vm = vmClass.getMethod("attach", String.class).invoke(null, pid);
        try {
            vmClass.getMethod("loadAgent", String.class, String.class).invoke(vm, agentJar, agentArgs);
            System.out.println("[ghost-attach] loaded agent into pid " + pid);
            System.out.println("[ghost-attach] java.home=" + System.getProperty("java.home"));
        } finally {
            try {
                vmClass.getMethod("detach").invoke(vm);
            } catch (Throwable ignored) {
            }
        }
    }
}
