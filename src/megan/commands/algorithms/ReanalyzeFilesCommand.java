/*
 * ReanalyzeFilesCommand.java Copyright (C) 2020. Daniel H. Huson
 *
 *  (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package megan.commands.algorithms;

import jloda.fx.util.ProgramExecutorService;
import jloda.swing.commands.CommandManager;
import jloda.swing.commands.ICommand;
import jloda.swing.director.IDirectableViewer;
import jloda.swing.director.IDirector;
import jloda.swing.director.ProjectManager;
import jloda.swing.window.NotificationsInSwing;
import jloda.util.Basic;
import jloda.util.CanceledException;
import jloda.util.ProgressListener;
import jloda.util.ProgressPercentage;
import jloda.util.parse.NexusStreamParser;
import jloda.util.parse.NexusStreamTokenizer;
import megan.classification.Classification;
import megan.classification.ClassificationManager;
import megan.commands.CommandBase;
import megan.core.ContaminantManager;
import megan.core.Director;
import megan.core.Document;
import megan.dialogs.lrinspector.LRInspectorViewer;
import megan.inspector.InspectorWindow;
import megan.viewer.MainViewer;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;

/**
 * reanalyze a set of files
 * Daniel Huson, 12.2019
 */
public class ReanalyzeFilesCommand extends CommandBase implements ICommand {
    public String getSyntax() {
        return "reanalyzeFiles file=<name> [,<name>...] [minSupportPercent=<number>] [minSupport=<number>] [minScore=<number>] [maxExpected=<number>] [minPercentIdentity=<number>] [topPercent=<number>]\n" +
                "\t[lcaAlgorithm={"+Basic.toString(Document.LCAAlgorithm.values(),"|")+"}] [lcaCoveragePercent=<number>] [minPercentReadToCover=<number>]  [minPercentReferenceToCover=<number>]" +
                " [minComplexity=<number>] [longReads={false|true}] [pairedReads={false|true}] [useIdentityFilter={false|true}]\n" +
                "\t[useContaminantFilter={false|true}] [loadContaminantFile=<filename>]\n" +
                "\t[readAssignmentMode={" + Basic.toString(Document.ReadAssignmentMode.values(), "|") + "} [fNames={" + Basic.toString(ClassificationManager.getAllSupportedClassificationsExcludingNCBITaxonomy(), "|") + "|*}];";
    }

    public void apply(NexusStreamParser np) throws Exception {
        np.matchIgnoreCase("reanalyzeFiles file=");

        final ArrayList<String> files = new ArrayList<>();
        files.add(np.getWordFileNamePunctuation());

        while (np.peekMatchAnyTokenIgnoreCase(",")) {
            np.matchIgnoreCase(",");
            files.add(np.getWordFileNamePunctuation());
        }

        final ProgressListener progress= (getDoc()!=null?getDoc().getProgressListener():new ProgressPercentage());

        progress.setMaximum(files.size());
        progress.setProgress(0);

        final List<String> tokens=np.getTokensRespectCase(null,";");
        final String fNames=np.findIgnoreCase(tokens,"fName=",null,"*");
        final boolean allFNames=(fNames.equals("*"));

        final String recomputeParameters=Basic.toString(tokens," ").replaceAll("fNames\\s*=.*","");

        for(String file:files) {
            progress.setTasks("Reanalyzing",file);
            {
                final Director openDir = findOpenDirector(file);
                if (openDir != null) {
                    NotificationsInSwing.showWarning("File '"+file+"' is currently open, cannot reanalyze open files");
                    continue;
                }
            }
            NotificationsInSwing.showInformation("Reanalyzing file: " + file);

            final Director dir=Director.newProject(false,true);

            try {
                final Document doc = dir.getDocument();
                doc.setOpenDAAFileOnlyIfMeganized(false);
                doc.getMeganFile().setFileFromExistingFile(file, false);

                final String fNamesToUse = (allFNames? Basic.toString(Basic.remove(doc.getConnector().getAllClassificationNames(), "Taxonomy")," "):fNames).trim();

                final String recomputeCommand = "recompute " + recomputeParameters +(fNamesToUse.length()>0? " fNames = " + fNamesToUse:"") + ";";

                dir.getDocument().setProgressListener(getDoc().getProgressListener());
                dir.executeImmediately(recomputeCommand, dir.getMainViewer().getCommandManager());
            }
            finally {
                dir.close();
            }
            progress.incrementProgress();
        }
        //getDir().close();
    }

    public void actionPerformed(ActionEvent event) {
    }

    public boolean isApplicable() {
        return true;
    }

    public String getName() {
        return "Reanalyze Files";
    }

    public String getDescription() {
        return "Reanalyze files";
    }

    public boolean isCritical() {
        return true;
    }

    public KeyStroke getAcceleratorKey() {
        return null;
    }

    public ImageIcon getIcon() {
        return null;
    }

    /**
     * get directory if this file is currently open
     *
     * @param daaFile
     * @return directory
     */
    private Director findOpenDirector(String daaFile) {
        final File file = new File(daaFile);
        if (file.isFile()) {
            for (IDirector dir : ProjectManager.getProjects()) {
                File aFile = new File(((Director) dir).getDocument().getMeganFile().getFileName());
                if (aFile.isFile() && aFile.equals(file))
                    return (Director) dir;
            }
        }
        return null;
    }
}


