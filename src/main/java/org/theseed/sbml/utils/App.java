package org.theseed.sbml.utils;

import java.util.Arrays;

import org.theseed.basic.BaseProcessor;

/**
 * Commands for utilities relating to RNA-Seq processing.
 *
 * import		import an SBML file into an Escher model
 */
public class App
{
    public static void main( String[] args )
    {
        // Get the control parameter.
        String command = args[0];
        String[] newArgs = Arrays.copyOfRange(args, 1, args.length);
        BaseProcessor processor;
        // Determine the command to process.
        switch (command) {
        case "import" :
            processor = new SbmlImportProcessor();
            break;
        default:
            throw new RuntimeException("Invalid command " + command);
        }
        // Process it.
        boolean ok = processor.parseCommand(newArgs);
        if (ok) {
            processor.run();
        }
    }
}
