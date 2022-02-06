/**
 *
 */
package org.theseed.metabolism;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.sbml.jsbml.Model;
import org.sbml.jsbml.SpeciesReference;
import org.sbml.jsbml.ext.fbc.Association;
import org.sbml.jsbml.ext.fbc.FBCModelPlugin;
import org.sbml.jsbml.ext.fbc.FBCReactionPlugin;
import org.sbml.jsbml.ext.fbc.GeneProductRef;
import org.sbml.jsbml.ext.fbc.LogicalOperator;
import org.sbml.jsbml.ext.fbc.Or;
import org.theseed.genome.Genome;

/**
 * This is an extended version of the metabolic model that can be augmented by an SBML file.  It is
 * kept isolated from the main class because of the general craziness of the JSBML package, which
 * brings in a bunch of log4j bugs we don't need.
 *
 * @author Bruce Parrello
 *
 */
public class SbmlMetaModel extends MetaModel {

    /**
     * Create a metabolic model.
     *
     * @param inFile		input file containing the Escher Map definition
     * @param genome		relevant base genome
     *
     * @throws IOException
     */
    public SbmlMetaModel(File inFile, Genome genome) throws IOException {
        super(inFile, genome);
    }

    /**
     * Add an SBML model to this map.  Currently, we just add the reactions, and
     * do not create nodes or segments.  The result is good enough for analysis
     * of pathways.
     *
     * The SBML model must use Argonne naming conventions:  each ID consists of a
     * prefix ("X_", where "X" indicates the type) plus the BiGG ID.
     *
     * @param sbmlModel		SBML model to import
     */
    public void importSbml(Model sbmlModel) {
        int newReactionCount = 0;
        // We will need the alias map for the base genome.
        var aliasMap = this.getBaseGenome().getAliasMap();
        // Get an FBC-aware version of the model.
        var fbcModel = (FBCModelPlugin) sbmlModel.getExtension("fbc");
        // The basic strategy is to import the reactions, adding the metabolites as
        // needed.
        final int rN = sbmlModel.getReactionCount();
        for (int rI = 0; rI < rN; rI++) {
            var newReaction = sbmlModel.getReaction(rI);
            // Get the BiGG ID of the reaction.  Only proceed if the reaction is
            // not already present.
            String rBiggId = StringUtils.removeStart(newReaction.getId(), "R_");
            if (! this.getBReactionMap().containsKey(rBiggId)) {
                newReactionCount++;
                // Get an ID for this reaction.
                int reactionId = this.getNextId();
                Reaction reaction = new Reaction(reactionId, rBiggId, newReaction.getName());
                reaction.setReversible(newReaction.getReversible());
                // The things we care about are the reaction rule and that stoichiometric
                // formula.  The reaction rule is first.
                var products = this.setSbmlReactionRule(reaction, newReaction);
                this.getBReactionMap().put(rBiggId, reaction);
                // The reaction still needs the gene aliases.  We find these in the
                // gene product records.
                for (String product : products) {
                    var productRef = fbcModel.getGeneProduct(product);
                    reaction.addAlias(productRef.getLabel(), productRef.getName());
                }
                // Finally, connect the reaction to its features.
                this.connectReaction(aliasMap, reaction);
                // Build the stoichiometry.
                newReaction.getListOfProducts().forEach(x -> this.addStoich(reaction, x, 1));
                newReaction.getListOfReactants().forEach(x -> this.addStoich(reaction, x, -1));
                // Update the networking maps.
                this.createReactionNetwork(reaction);
            }
        }
        log.info("{} new reactions found.", newReactionCount);
    }


    /**
     * Add the stoichiometry from an SBML species reference to this reaction.
     *
     * @param x			source SBML species reference
     * @param factor	1 for a product, -1 for a reactant
     */
    protected void addStoich(Reaction reaction, SpeciesReference x, int factor) {
        String metabolite = StringUtils.removeStart(x.getSpecies(), "M_");
        int coeff = ((int) x.getStoichiometry()) * factor;
        reaction.addStoich(coeff, metabolite);
    }


    /**
     * Compute the reaction rule for an SBML reaction node
     *
     * @param reaction		reaction object to update
     * @param newReaction	SBML reaction node to parse
     *
     * @return the IDs of the gene products used in the rule
     */
    private Set<String> setSbmlReactionRule(Reaction reaction, org.sbml.jsbml.Reaction newReaction) {
        Set<String> retVal = new TreeSet<String>();
        // Get an FBC-enabled version of the reaction.
        var trigger = ((FBCReactionPlugin) newReaction.getExtension("fbc"))
                .getGeneProductAssociation();
        if (trigger != null) {
            var rule = trigger.getAssociation();
            // We will track the aliases in here.
            String ruleString = this.processRule(rule, retVal);
            reaction.setRule(ruleString);
        }
        return retVal;
    }

    /**
     * Recursively parse this rule into a reaction rule string.
     *
     * @param rule		rule to parse (as an XML tree)
     * @param genes		place to save gene products found
     *
     * @return a rule expression
     */
    private String processRule(Association rule, Set<String> genes) {
        String retVal;
        if (rule instanceof GeneProductRef) {
            // Here we have a leaf.
            String id = ((GeneProductRef) rule).getGeneProduct();
            retVal = StringUtils.removeStart(id, "G_");
            genes.add(id);
        } else {
            // Here we have a logical operator.
            LogicalOperator op = ((LogicalOperator) rule);
            // Compute the operator type.
            String operation = (op instanceof Or ? " or " : " and ");
            // Join the children.
            retVal = op.getListOfAssociations().stream().map(x -> this.processRule(x, genes))
                    .collect(Collectors.joining(operation, "(", ")"));
        }
        return retVal;
    }

}
