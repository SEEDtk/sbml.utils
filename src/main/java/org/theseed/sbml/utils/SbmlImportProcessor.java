/**
 *
 */
package org.theseed.sbml.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.kohsuke.args4j.Argument;
import org.sbml.jsbml.SBMLDocument;
import org.sbml.jsbml.SBMLReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.theseed.basic.BaseProcessor;
import org.theseed.basic.ParseFailureException;
import org.theseed.genome.Genome;
import org.theseed.metabolism.SbmlMetaModel;

/**
 * This command reads an Escher Map JSON file, imports the reactions from an SBML model file, and writes
 * them back to a new file.
 *
 * The positional parameters are the name of the input file, the name of the base genome GTO file, the name of
 * the SBML file, and the name of the output file.
 *
 * The command-line options are as follows:
 *
 * -h	display command-line usage
 * -v	display more detailed log messages
 *
 * @author Bruce Parrello
 *
 */
public class SbmlImportProcessor extends BaseProcessor {

    // FIELDS
    /** logging facility */
    protected static Logger log = LoggerFactory.getLogger(SbmlImportProcessor.class);
    /** base genome */
    private Genome baseGenome;

    // COMMAND-LINE OPTIONS

    /** input JSON file */
    @Argument(index = 0, metaVar = "input.json", usage = "Escher map input file", required = true)
    private File inFile;

    /** input GTO file */
    @Argument(index = 1, metaVar = "input.gto", usage = "base genome GTO file", required = true)
    private File genomeFile;

    /** input SBML file */
    @Argument(index = 2, metaVar = "input.sbml.xml", usage = "SBML containing additional reactions", required = true)
    private File sbmlFile;

    /** output file */
    @Argument(index = 3, metaVar = "output.json", usage = "Escher map output file", required = true)
    private File outFile;

    @Override
    protected void setDefaults() {
    }

    @Override
    protected boolean validateParms() throws IOException, ParseFailureException {
        if (! this.inFile.canRead())
            throw new FileNotFoundException("Input JSON file " + this.inFile + " is not found or unreadable.");
        if (! this.sbmlFile.canRead())
            throw new FileNotFoundException("Input SBML file " + this.inFile + " is not found or unreadable.");
        File outDir = this.outFile.getAbsoluteFile().getParentFile();
        if (! outDir.isDirectory() || ! outDir.canExecute())
            throw new FileNotFoundException("Invalid or missing directory for output file " + this.outFile + ".");
        if (this.outFile.exists() && ! this.outFile.canWrite())
            throw new FileNotFoundException("Output file " + this.outFile + " is not writable.");
        if (! this.genomeFile.canRead())
            throw new FileNotFoundException("Input base-genome file " + this.genomeFile + " is not found or unreadable.");
        this.baseGenome = new Genome(this.genomeFile);
        log.info("Base genome {} loaded from {}.", this.baseGenome, this.genomeFile);
        return true;
    }

    @Override
    protected void runCommand() throws Exception {
        // Load the escher map.
        SbmlMetaModel escherMap = new SbmlMetaModel(this.inFile, this.baseGenome);
        // Import the SBML.
        log.info("Reading SBML model from {}.", this.sbmlFile);
        SBMLDocument doc = SBMLReader.read(this.sbmlFile);
        escherMap.importSbml(doc.getModel());
        // Write the result.
        escherMap.save(this.outFile);
    }

}
