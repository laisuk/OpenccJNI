package openccjnicli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "openccjnicli",
        mixinStandardHelpOptions = true,
        version = "1.0.0",
        description = "\033[1;34mJava JNI OpenCC (OpenccJNI) CLI with multiple tools\033[0m",
        subcommands = {
                ConvertCommand.class,
                OfficeCommand.class,
        }
)
public class Main implements Runnable {

    @Override
    public void run() {
        // Called when no subcommand is provided
        System.out.println("Use --help or a subcommand [convert | office]");
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}
