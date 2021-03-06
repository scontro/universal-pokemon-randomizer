/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.dabomstew.pkrandom.gui;

/*----------------------------------------------------------------------------*/
/*--  RandomizerGUI.java - the main GUI for the randomizer, containing the	--*/
/*--					   various options available and such.				--*/
/*--  																		--*/
/*--  Part of "Universal Pokemon Randomizer" by Dabomstew					--*/
/*--  Pokemon and any associated names and the like are						--*/
/*--  trademark and (C) Nintendo 1996-2012.									--*/
/*--  																		--*/
/*--  The custom code written here is licensed under the terms of the GPL:	--*/
/*--                                                                        --*/
/*--  This program is free software: you can redistribute it and/or modify  --*/
/*--  it under the terms of the GNU General Public License as published by  --*/
/*--  the Free Software Foundation, either version 3 of the License, or     --*/
/*--  (at your option) any later version.                                   --*/
/*--                                                                        --*/
/*--  This program is distributed in the hope that it will be useful,       --*/
/*--  but WITHOUT ANY WARRANTY; without even the implied warranty of        --*/
/*--  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the          --*/
/*--  GNU General Public License for more details.                          --*/
/*--                                                                        --*/
/*--  You should have received a copy of the GNU General Public License     --*/
/*--  along with this program. If not, see <http://www.gnu.org/licenses/>.  --*/
/*----------------------------------------------------------------------------*/

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.xml.bind.DatatypeConverter;

import com.dabomstew.pkrandom.FileFunctions;
import com.dabomstew.pkrandom.InvalidSupplementFilesException;
import com.dabomstew.pkrandom.RandomSource;
import com.dabomstew.pkrandom.Randomizer;
import com.dabomstew.pkrandom.Settings;
import com.dabomstew.pkrandom.Utils;
import com.dabomstew.pkrandom.pokemon.GenRestrictions;
import com.dabomstew.pkrandom.pokemon.Pokemon;
import com.dabomstew.pkrandom.romhandlers.AbstractDSRomHandler;
import com.dabomstew.pkrandom.romhandlers.Gen1RomHandler;
import com.dabomstew.pkrandom.romhandlers.Gen2RomHandler;
import com.dabomstew.pkrandom.romhandlers.Gen3RomHandler;
import com.dabomstew.pkrandom.romhandlers.Gen4RomHandler;
import com.dabomstew.pkrandom.romhandlers.Gen5RomHandler;
import com.dabomstew.pkrandom.romhandlers.RomHandler;

/**
 * 
 * @author Stewart
 */
public class RandomizerGUI extends javax.swing.JFrame {

	/**
	 * 
	 */
	private static final long serialVersionUID = 637989089525556154L;
	private RomHandler romHandler;
	protected RomHandler.Factory[] checkHandlers;

	public static final int UPDATE_VERSION = 1632;

	public static PrintStream verboseLog = System.out;

	private OperationDialog opDialog;
	private boolean presetMode;
	private GenRestrictions currentRestrictions;
	private int currentCodeTweaks;

	private static String rootPath = "./";

	// Settings
	private boolean autoUpdateEnabled;
	private boolean haveCheckedCustomNames;

	java.util.ResourceBundle bundle;

	/**
	 * @param args
	 *            the command line arguments
	 */
	public static void main(String args[]) {
		boolean autoupdate = true;
		for (String arg : args) {
			if (arg.equalsIgnoreCase("--noupdate")) {
				autoupdate = false;
				break;
			}
		}
		final boolean au = autoupdate;
		try {
			javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager
					.getSystemLookAndFeelClassName());
		} catch (ClassNotFoundException ex) {
			java.util.logging.Logger.getLogger(RandomizerGUI.class.getName())
					.log(java.util.logging.Level.SEVERE, null, ex);
		} catch (InstantiationException ex) {
			java.util.logging.Logger.getLogger(RandomizerGUI.class.getName())
					.log(java.util.logging.Level.SEVERE, null, ex);
		} catch (IllegalAccessException ex) {
			java.util.logging.Logger.getLogger(RandomizerGUI.class.getName())
					.log(java.util.logging.Level.SEVERE, null, ex);
		} catch (javax.swing.UnsupportedLookAndFeelException ex) {
			java.util.logging.Logger.getLogger(RandomizerGUI.class.getName())
					.log(java.util.logging.Level.SEVERE, null, ex);
		}

		/* Create and display the form */
		java.awt.EventQueue.invokeLater(new Runnable() {
			public void run() {
				new RandomizerGUI(au);
			}
		});
	}

	// constructor
	/**
	 * Creates new form RandomizerGUI
	 * 
	 * @param autoupdate
	 */
	public RandomizerGUI(boolean autoupdate) {

		try {
			URL location = RandomizerGUI.class.getProtectionDomain()
					.getCodeSource().getLocation();
			File fh = new File(java.net.URLDecoder.decode(location.getFile(),
					"UTF-8")).getParentFile();
			rootPath = fh.getAbsolutePath() + File.separator;
		} catch (Exception e) {
			rootPath = "./";
		}

		bundle = java.util.ResourceBundle
				.getBundle("com/dabomstew/pkrandom/gui/Bundle"); // NOI18N
		testForRequiredConfigs();
		checkHandlers = new RomHandler.Factory[] {
				new Gen1RomHandler.Factory(), new Gen2RomHandler.Factory(),
				new Gen3RomHandler.Factory(), new Gen4RomHandler.Factory(),
				new Gen5RomHandler.Factory() };
		initComponents();
		initialiseState();
		autoUpdateEnabled = true;
		haveCheckedCustomNames = false;
		attemptReadConfig();
		if (!autoupdate) {
			// override autoupdate
			autoUpdateEnabled = false;
		}
		boolean canWrite = attemptWriteConfig();
		if (!canWrite) {
			JOptionPane.showMessageDialog(null,
					bundle.getString("RandomizerGUI.cantWriteConfigFile"));
			autoUpdateEnabled = false;
		}
		setLocationRelativeTo(null);
		setVisible(true);
		checkCustomNames();
		if (autoUpdateEnabled) {
			new UpdateCheckThread(this, false).start();
		}
	}

	// config-related stuff

	private void checkCustomNames() {
		String[] cnamefiles = new String[] { "trainerclasses.txt",
				"trainernames.txt", "nicknames.txt" };
		int[] defaultcsums = new int[] { -1442281799, -1499590809, 1641673648 };

		boolean foundCustom = false;
		for (int file = 0; file < 3; file++) {
			File oldFile = new File(rootPath + "/config/" + cnamefiles[file]);
			File currentFile = new File(rootPath + cnamefiles[file]);
			if (oldFile.exists() && oldFile.canRead() && !currentFile.exists()) {
				try {
					int crc = FileFunctions
							.getFileChecksum(new FileInputStream(oldFile));
					if (crc != defaultcsums[file]) {
						foundCustom = true;
						break;
					}
				} catch (FileNotFoundException e) {
				}
			}
		}

		if (foundCustom) {
			int response = JOptionPane
					.showConfirmDialog(
							RandomizerGUI.this,
							bundle.getString("RandomizerGUI.copyNameFilesDialog.text"),
							bundle.getString("RandomizerGUI.copyNameFilesDialog.title"),
							JOptionPane.YES_NO_OPTION);
			boolean onefailed = false;
			if (response == JOptionPane.YES_OPTION) {
				for (String filename : cnamefiles) {
					if (new File(rootPath + "/config/" + filename).canRead()) {
						try {
							FileInputStream fis = new FileInputStream(new File(
									rootPath + "config/" + filename));
							FileOutputStream fos = new FileOutputStream(
									new File(rootPath + filename));
							byte[] buf = new byte[1024];
							int len;
							while ((len = fis.read(buf)) > 0) {
								fos.write(buf, 0, len);
							}
							fos.close();
							fis.close();
						} catch (IOException ex) {
							onefailed = true;
						}
					}
				}
				if (onefailed) {
					JOptionPane.showMessageDialog(this, bundle
							.getString("RandomizerGUI.copyNameFilesFailed"));
				}
			}
		}

		haveCheckedCustomNames = true;
		attemptWriteConfig();
	}

	private void attemptReadConfig() {
		File fh = new File(rootPath + "config.ini");
		if (!fh.exists() || !fh.canRead()) {
			return;
		}

		try {
			Scanner sc = new Scanner(fh, "UTF-8");
			while (sc.hasNextLine()) {
				String q = sc.nextLine().trim();
				if (q.contains("//")) {
					q = q.substring(0, q.indexOf("//")).trim();
				}
				if (!q.isEmpty()) {
					String[] tokens = q.split("=", 2);
					if (tokens.length == 2) {
						String key = tokens[0].trim();
						if (key.equalsIgnoreCase("autoupdate")) {
							autoUpdateEnabled = Boolean.parseBoolean(tokens[1]
									.trim());
						} else if (key.equalsIgnoreCase("checkedcustomnames")) {
							haveCheckedCustomNames = Boolean
									.parseBoolean(tokens[1].trim());
						}
					}
				}
			}
			sc.close();
		} catch (IOException ex) {

		}
	}

	private boolean attemptWriteConfig() {
		File fh = new File(rootPath + "config.ini");
		if (fh.exists() && !fh.canWrite()) {
			return false;
		}

		try {
			PrintStream ps = new PrintStream(new FileOutputStream(fh), true,
					"UTF-8");
			ps.println("autoupdate=" + autoUpdateEnabled);
			ps.println("checkedcustomnames=" + haveCheckedCustomNames);
			ps.close();
			return true;
		} catch (IOException e) {
			return false;
		}

	}

	private void testForRequiredConfigs() {
		try {
			Utils.testForRequiredConfigs();
		} catch (FileNotFoundException e) {
			JOptionPane.showMessageDialog(null, String.format(
					bundle.getString("RandomizerGUI.configFileMissing"),
					e.getMessage()));
			System.exit(1);
			return;
		}
	}

	// form initial state

	private void initialiseState() {
		this.romHandler = null;
		this.currentRestrictions = null;
		this.currentCodeTweaks = 0;
		updateCodeTweaksButtonText();
		initialFormState();
		this.romOpenChooser.setCurrentDirectory(new File(rootPath));
		this.romSaveChooser.setCurrentDirectory(new File(rootPath));
		if (new File(rootPath + "settings/").exists()) {
			this.qsOpenChooser.setCurrentDirectory(new File(rootPath
					+ "settings/"));
			this.qsSaveChooser.setCurrentDirectory(new File(rootPath
					+ "settings/"));
		} else {
			this.qsOpenChooser.setCurrentDirectory(new File(rootPath));
			this.qsSaveChooser.setCurrentDirectory(new File(rootPath));
		}
	}

	private void initialFormState() {
		// Disable all rom components
		this.goRemoveTradeEvosCheckBox.setEnabled(false);
		this.goUpdateMovesCheckBox.setEnabled(false);
		this.goUpdateMovesLegacyCheckBox.setEnabled(false);
		this.goUpdateTypesCheckBox.setEnabled(false);
		this.goLowerCaseNamesCheckBox.setEnabled(false);
		this.goNationalDexCheckBox.setEnabled(false);
		this.goCondenseEvosCheckBox.setEnabled(false);

		this.goRemoveTradeEvosCheckBox.setSelected(false);
		this.goUpdateMovesCheckBox.setSelected(false);
		this.goUpdateMovesLegacyCheckBox.setSelected(false);
		this.goUpdateTypesCheckBox.setSelected(false);
		this.goLowerCaseNamesCheckBox.setSelected(false);
		this.goNationalDexCheckBox.setSelected(false);
		this.goCondenseEvosCheckBox.setSelected(false);

		this.goUpdateMovesLegacyCheckBox.setVisible(true);

		this.codeTweaksCB.setEnabled(false);
		this.codeTweaksCB.setSelected(false);
		this.codeTweaksBtn.setEnabled(false);
		this.codeTweaksBtn.setVisible(true);
		this.codeTweaksCB.setVisible(true);
		this.pokeLimitCB.setEnabled(false);
		this.pokeLimitCB.setSelected(false);
		this.pokeLimitBtn.setEnabled(false);
		this.pokeLimitBtn.setVisible(true);
		this.pokeLimitCB.setVisible(true);
		this.raceModeCB.setEnabled(false);
		this.raceModeCB.setSelected(false);
		this.randomizeHollowsCB.setEnabled(false);
		this.randomizeHollowsCB.setSelected(false);
		this.brokenMovesCB.setEnabled(false);
		this.brokenMovesCB.setSelected(false);

		this.riRomNameLabel.setText(bundle
				.getString("RandomizerGUI.noRomLoaded"));
		this.riRomCodeLabel.setText("");
		this.riRomSupportLabel.setText("");

		this.loadQSButton.setEnabled(false);
		this.saveQSButton.setEnabled(false);

		this.pbsChangesUnchangedRB.setEnabled(false);
		this.pbsChangesRandomEvosRB.setEnabled(false);
		this.pbsChangesRandomTotalRB.setEnabled(false);
		this.pbsChangesShuffleRB.setEnabled(false);
		this.pbsChangesUnchangedRB.setSelected(true);
		this.pbsStandardEXPCurvesCB.setEnabled(false);
		this.pbsStandardEXPCurvesCB.setSelected(false);

		this.abilitiesPanel.setVisible(true);
		this.paUnchangedRB.setEnabled(false);
		this.paRandomizeRB.setEnabled(false);
		this.paWonderGuardCB.setEnabled(false);
		this.paUnchangedRB.setSelected(true);
		this.paWonderGuardCB.setSelected(false);

		this.spCustomPoke1Chooser.setEnabled(false);
		this.spCustomPoke2Chooser.setEnabled(false);
		this.spCustomPoke3Chooser.setEnabled(false);
		this.spCustomPoke1Chooser.setSelectedIndex(0);
		this.spCustomPoke1Chooser.setModel(new DefaultComboBoxModel(
				new String[] { "--" }));
		this.spCustomPoke2Chooser.setSelectedIndex(0);
		this.spCustomPoke2Chooser.setModel(new DefaultComboBoxModel(
				new String[] { "--" }));
		this.spCustomPoke3Chooser.setSelectedIndex(0);
		this.spCustomPoke3Chooser.setModel(new DefaultComboBoxModel(
				new String[] { "--" }));
		this.spCustomRB.setEnabled(false);
		this.spRandomRB.setEnabled(false);
		this.spRandom2EvosRB.setEnabled(false);
		this.spUnchangedRB.setEnabled(false);
		this.spUnchangedRB.setSelected(true);
		this.spHeldItemsCB.setEnabled(false);
		this.spHeldItemsCB.setSelected(false);
		this.spHeldItemsCB.setVisible(true);
		this.spHeldItemsBanBadCB.setEnabled(false);
		this.spHeldItemsBanBadCB.setSelected(false);
		this.spHeldItemsBanBadCB.setVisible(true);

		this.pmsRandomTotalRB.setEnabled(false);
		this.pmsRandomTypeRB.setEnabled(false);
		this.pmsUnchangedRB.setEnabled(false);
		this.pmsUnchangedRB.setSelected(true);
		this.pmsMetronomeOnlyRB.setEnabled(false);
		this.pms4MovesCB.setEnabled(false);
		this.pms4MovesCB.setSelected(false);
		this.pms4MovesCB.setVisible(true);

		this.ptRandomFollowEvosRB.setEnabled(false);
		this.ptRandomTotalRB.setEnabled(false);
		this.ptUnchangedRB.setEnabled(false);
		this.ptUnchangedRB.setSelected(true);

		this.tpPowerLevelsCB.setEnabled(false);
		this.tpRandomRB.setEnabled(false);
		this.tpRivalCarriesStarterCB.setEnabled(false);
		this.tpTypeThemedRB.setEnabled(false);
		this.tpTypeWeightingCB.setEnabled(false);
		this.tpNoLegendariesCB.setEnabled(false);
		this.tpNoEarlyShedinjaCB.setEnabled(false);
		this.tpNoEarlyShedinjaCB.setVisible(true);
		this.tpUnchangedRB.setEnabled(false);
		this.tpUnchangedRB.setSelected(true);
		this.tpPowerLevelsCB.setSelected(false);
		this.tpRivalCarriesStarterCB.setSelected(false);
		this.tpTypeWeightingCB.setSelected(false);
		this.tpNoLegendariesCB.setSelected(false);
		this.tpNoEarlyShedinjaCB.setSelected(false);

		this.tnRandomizeCB.setEnabled(false);
		this.tcnRandomizeCB.setEnabled(false);

		this.tnRandomizeCB.setSelected(false);
		this.tcnRandomizeCB.setSelected(false);

		this.wpUnchangedRB.setEnabled(false);
		this.wpRandomRB.setEnabled(false);
		this.wpArea11RB.setEnabled(false);
		this.wpGlobalRB.setEnabled(false);
		this.wpUnchangedRB.setSelected(true);

		this.wpARNoneRB.setEnabled(false);
		this.wpARCatchEmAllRB.setEnabled(false);
		this.wpARTypeThemedRB.setEnabled(false);
		this.wpARSimilarStrengthRB.setEnabled(false);
		this.wpARNoneRB.setSelected(true);

		this.wpUseTimeCB.setEnabled(false);
		this.wpUseTimeCB.setVisible(true);
		this.wpUseTimeCB.setSelected(false);

		this.wpNoLegendariesCB.setEnabled(false);
		this.wpNoLegendariesCB.setSelected(false);

		this.wpCatchRateCB.setEnabled(false);
		this.wpCatchRateCB.setSelected(false);

		this.wpHeldItemsCB.setEnabled(false);
		this.wpHeldItemsCB.setSelected(false);
		this.wpHeldItemsCB.setVisible(true);
		this.wpHeldItemsBanBadCB.setEnabled(false);
		this.wpHeldItemsBanBadCB.setSelected(false);
		this.wpHeldItemsBanBadCB.setVisible(true);

		this.stpRandomL4LRB.setEnabled(false);
		this.stpRandomTotalRB.setEnabled(false);
		this.stpUnchangedRB.setEnabled(false);
		this.stpUnchangedRB.setSelected(true);

		this.tmmRandomRB.setEnabled(false);
		this.tmmUnchangedRB.setEnabled(false);
		this.tmmUnchangedRB.setSelected(true);

		this.thcRandomTotalRB.setEnabled(false);
		this.thcRandomTypeRB.setEnabled(false);
		this.thcUnchangedRB.setEnabled(false);
		this.thcFullRB.setEnabled(false);
		this.thcUnchangedRB.setSelected(true);

		this.tmLearningSanityCB.setEnabled(false);
		this.tmLearningSanityCB.setSelected(false);
		this.tmKeepFieldMovesCB.setEnabled(false);
		this.tmKeepFieldMovesCB.setSelected(false);

		this.mtmRandomRB.setEnabled(false);
		this.mtmUnchangedRB.setEnabled(false);
		this.mtmUnchangedRB.setSelected(true);

		this.mtcRandomTotalRB.setEnabled(false);
		this.mtcRandomTypeRB.setEnabled(false);
		this.mtcUnchangedRB.setEnabled(false);
		this.mtcFullRB.setEnabled(false);
		this.mtcUnchangedRB.setSelected(true);

		this.mtLearningSanityCB.setEnabled(false);
		this.mtLearningSanityCB.setSelected(false);
		this.mtKeepFieldMovesCB.setEnabled(false);
		this.mtKeepFieldMovesCB.setSelected(false);

		this.mtMovesPanel.setVisible(true);
		this.mtCompatPanel.setVisible(true);
		this.mtNoExistLabel.setVisible(false);

		this.igtUnchangedRB.setEnabled(false);
		this.igtGivenOnlyRB.setEnabled(false);
		this.igtBothRB.setEnabled(false);
		this.igtUnchangedRB.setSelected(true);

		this.igtRandomItemCB.setEnabled(false);
		this.igtRandomItemCB.setSelected(false);
		this.igtRandomItemCB.setVisible(true);

		this.igtRandomIVsCB.setEnabled(false);
		this.igtRandomIVsCB.setSelected(false);
		this.igtRandomIVsCB.setVisible(true);

		this.igtRandomOTCB.setEnabled(false);
		this.igtRandomOTCB.setSelected(false);
		this.igtRandomOTCB.setVisible(true);

		this.igtRandomNicknameCB.setEnabled(false);
		this.igtRandomNicknameCB.setSelected(false);

		this.fiUnchangedRB.setEnabled(false);
		this.fiShuffleRB.setEnabled(false);
		this.fiRandomRB.setEnabled(false);
		this.fiUnchangedRB.setSelected(true);

		this.fiBanBadCB.setEnabled(false);
		this.fiBanBadCB.setSelected(false);
		this.fiBanBadCB.setVisible(true);

	}

	// rom loading

	private void loadROM() {
		romOpenChooser.setSelectedFile(null);
		int returnVal = romOpenChooser.showOpenDialog(this);
		if (returnVal == JFileChooser.APPROVE_OPTION) {
			final File fh = romOpenChooser.getSelectedFile();
			try {
				Utils.validateRomFile(fh);
			} catch (Utils.InvalidROMException e) {
				switch (e.getType()) {
				case LENGTH:
					JOptionPane.showMessageDialog(this, String.format(
							bundle.getString("RandomizerGUI.tooShortToBeARom"),
							fh.getName()));
					return;
				case ZIP_FILE:
					JOptionPane.showMessageDialog(this, String.format(
							bundle.getString("RandomizerGUI.openedZIPfile"),
							fh.getName()));
					return;
				case RAR_FILE:
					JOptionPane.showMessageDialog(this, String.format(
							bundle.getString("RandomizerGUI.openedRARfile"),
							fh.getName()));
					return;
				case IPS_FILE:
					JOptionPane.showMessageDialog(this, String.format(
							bundle.getString("RandomizerGUI.openedIPSfile"),
							fh.getName()));
					return;
				case UNREADABLE:
					JOptionPane.showMessageDialog(this, String.format(
							bundle.getString("RandomizerGUI.unreadableRom"),
							fh.getName()));
					return;
				}
			}

			for (RomHandler.Factory rhf : checkHandlers) {
				if (rhf.isLoadable(fh.getAbsolutePath())) {
					this.romHandler = rhf.create(RandomSource.instance());
					opDialog = new OperationDialog(
							bundle.getString("RandomizerGUI.loadingText"),
							this, true);
					Thread t = new Thread() {
						@Override
						public void run() {
							SwingUtilities.invokeLater(new Runnable() {
								@Override
								public void run() {
									opDialog.setVisible(true);
								}
							});
							try {
								RandomizerGUI.this.romHandler.loadRom(fh
										.getAbsolutePath());
							} catch (Exception ex) {
								long time = System.currentTimeMillis();
								try {
									String errlog = "error_" + time + ".txt";
									PrintStream ps = new PrintStream(
											new FileOutputStream(errlog));
									PrintStream e1 = System.err;
									System.setErr(ps);
									ex.printStackTrace();
									verboseLog.close();
									System.setErr(e1);
									ps.close();
									JOptionPane
											.showMessageDialog(
													RandomizerGUI.this,
													String.format(
															bundle.getString("RandomizerGUI.loadFailed"),
															errlog));
								} catch (Exception logex) {
									JOptionPane
											.showMessageDialog(
													RandomizerGUI.this,
													bundle.getString("RandomizerGUI.loadFailedNoLog"));
									verboseLog.close();
								}
							}
							SwingUtilities.invokeLater(new Runnable() {
								@Override
								public void run() {
									RandomizerGUI.this.opDialog
											.setVisible(false);
									RandomizerGUI.this.initialFormState();
									RandomizerGUI.this.romLoaded();
								}
							});
						}
					};
					t.start();

					return;
				}
			}
			JOptionPane.showMessageDialog(this, String.format(
					bundle.getString("RandomizerGUI.unsupportedRom"),
					fh.getName()));
		}

	}

	private void romLoaded() {
		try {
			this.currentRestrictions = null;
			this.currentCodeTweaks = 0;
			updateCodeTweaksButtonText();
			this.riRomNameLabel.setText(this.romHandler.getROMName());
			this.riRomCodeLabel.setText(this.romHandler.getROMCode());
			this.riRomSupportLabel.setText(bundle
					.getString("RandomizerGUI.romSupportPrefix")
					+ " "
					+ this.romHandler.getSupportLevel());
			this.goUpdateMovesCheckBox.setSelected(false);
			if (romHandler instanceof Gen1RomHandler) {
				this.goUpdateTypesCheckBox.setEnabled(true);
			}
			this.goUpdateMovesCheckBox.setSelected(false);
			this.goUpdateMovesCheckBox.setEnabled(true);
			this.goUpdateMovesLegacyCheckBox.setSelected(false);
			this.goUpdateMovesLegacyCheckBox.setEnabled(false);
			this.goUpdateMovesLegacyCheckBox
					.setVisible(!(romHandler instanceof Gen5RomHandler));
			this.goRemoveTradeEvosCheckBox.setSelected(false);
			this.goRemoveTradeEvosCheckBox.setEnabled(true);
			this.goCondenseEvosCheckBox.setSelected(false);
			this.goCondenseEvosCheckBox.setEnabled(true);
			if (!(romHandler instanceof Gen5RomHandler)) {
				this.goLowerCaseNamesCheckBox.setSelected(false);
				this.goLowerCaseNamesCheckBox.setEnabled(true);
			}
			if (romHandler instanceof Gen3RomHandler) {
				this.goNationalDexCheckBox.setSelected(false);
				this.goNationalDexCheckBox.setEnabled(true);
			}
			this.raceModeCB.setSelected(false);
			this.raceModeCB.setEnabled(true);

			this.codeTweaksCB.setSelected(false);
			this.codeTweaksCB.setEnabled(romHandler.codeTweaksAvailable() != 0);
			this.codeTweaksBtn.setEnabled(false);
			this.codeTweaksBtn
					.setVisible(romHandler.codeTweaksAvailable() != 0);
			this.codeTweaksCB.setVisible(romHandler.codeTweaksAvailable() != 0);

			this.pokeLimitCB.setSelected(false);
			this.pokeLimitBtn.setEnabled(false);
			this.pokeLimitCB
					.setEnabled(!(romHandler instanceof Gen1RomHandler || romHandler.isROMHack()));
			this.pokeLimitCB
					.setVisible(!(romHandler instanceof Gen1RomHandler || romHandler.isROMHack()));
			this.pokeLimitBtn
					.setVisible(!(romHandler instanceof Gen1RomHandler || romHandler.isROMHack()));

			this.randomizeHollowsCB.setSelected(false);
			this.randomizeHollowsCB.setEnabled(romHandler
					.hasHiddenHollowPokemon());

			this.brokenMovesCB.setSelected(false);
			this.brokenMovesCB.setEnabled(true);

			this.loadQSButton.setEnabled(true);
			this.saveQSButton.setEnabled(true);

			this.pbsChangesUnchangedRB.setEnabled(true);
			this.pbsChangesUnchangedRB.setSelected(true);
			this.pbsChangesRandomEvosRB.setEnabled(true);
			this.pbsChangesRandomTotalRB.setEnabled(true);
			this.pbsChangesShuffleRB.setEnabled(true);

			this.pbsStandardEXPCurvesCB.setEnabled(true);
			this.pbsStandardEXPCurvesCB.setSelected(false);

			if (romHandler.abilitiesPerPokemon() > 0) {
				this.paUnchangedRB.setEnabled(true);
				this.paUnchangedRB.setSelected(true);
				this.paRandomizeRB.setEnabled(true);
				this.paWonderGuardCB.setEnabled(false);
			} else {
				this.abilitiesPanel.setVisible(false);
			}

			this.spUnchangedRB.setEnabled(true);
			this.spUnchangedRB.setSelected(true);

			this.spCustomPoke3Chooser.setVisible(true);
			if (romHandler.canChangeStarters()) {
				this.spCustomRB.setEnabled(true);
				this.spRandomRB.setEnabled(true);
				this.spRandom2EvosRB.setEnabled(true);
				if (romHandler.isYellow()) {
					this.spCustomPoke3Chooser.setVisible(false);
				}
				populateDropdowns();
			}

			this.spHeldItemsCB.setSelected(false);
			boolean hasStarterHeldItems = (romHandler instanceof Gen2RomHandler || romHandler instanceof Gen3RomHandler);
			this.spHeldItemsCB.setEnabled(hasStarterHeldItems);
			this.spHeldItemsCB.setVisible(hasStarterHeldItems);
			this.spHeldItemsBanBadCB.setEnabled(false);
			this.spHeldItemsBanBadCB.setVisible(hasStarterHeldItems);

			this.pmsRandomTotalRB.setEnabled(true);
			this.pmsRandomTypeRB.setEnabled(true);
			this.pmsUnchangedRB.setEnabled(true);
			this.pmsUnchangedRB.setSelected(true);
			this.pmsMetronomeOnlyRB.setEnabled(true);

			this.pms4MovesCB.setVisible(romHandler.supportsFourStartingMoves());

			this.ptRandomFollowEvosRB.setEnabled(true);
			this.ptRandomTotalRB.setEnabled(true);
			this.ptUnchangedRB.setEnabled(true);
			this.ptUnchangedRB.setSelected(true);

			this.tpRandomRB.setEnabled(true);
			this.tpTypeThemedRB.setEnabled(true);
			this.tpUnchangedRB.setEnabled(true);
			this.tpUnchangedRB.setSelected(true);
			this.tnRandomizeCB.setEnabled(true);
			this.tcnRandomizeCB.setEnabled(true);

			if (romHandler instanceof Gen1RomHandler
					|| romHandler instanceof Gen2RomHandler) {
				this.tpNoEarlyShedinjaCB.setVisible(false);
			} else {
				this.tpNoEarlyShedinjaCB.setVisible(true);
			}
			this.tpNoEarlyShedinjaCB.setSelected(false);

			this.wpArea11RB.setEnabled(true);
			this.wpGlobalRB.setEnabled(true);
			this.wpRandomRB.setEnabled(true);
			this.wpUnchangedRB.setEnabled(true);
			this.wpUnchangedRB.setSelected(true);
			this.wpUseTimeCB.setEnabled(false);
			this.wpNoLegendariesCB.setEnabled(false);
			if (!romHandler.hasTimeBasedEncounters()) {
				this.wpUseTimeCB.setVisible(false);
			}
			this.wpCatchRateCB.setEnabled(true);

			this.wpHeldItemsCB.setSelected(false);
			this.wpHeldItemsCB.setEnabled(true);
			this.wpHeldItemsCB.setVisible(true);
			this.wpHeldItemsBanBadCB.setSelected(false);
			this.wpHeldItemsBanBadCB.setEnabled(false);
			this.wpHeldItemsBanBadCB.setVisible(true);
			if (romHandler instanceof Gen1RomHandler) {
				this.wpHeldItemsCB.setVisible(false);
				this.wpHeldItemsBanBadCB.setVisible(false);
			}

			this.stpUnchangedRB.setEnabled(true);
			if (this.romHandler.canChangeStaticPokemon()) {
				this.stpRandomL4LRB.setEnabled(true);
				this.stpRandomTotalRB.setEnabled(true);

			}

			this.tmmRandomRB.setEnabled(true);
			this.tmmUnchangedRB.setEnabled(true);

			this.thcRandomTotalRB.setEnabled(true);
			this.thcRandomTypeRB.setEnabled(true);
			this.thcUnchangedRB.setEnabled(true);
			this.thcFullRB.setEnabled(true);

			if (this.romHandler.hasMoveTutors()) {
				this.mtmRandomRB.setEnabled(true);
				this.mtmUnchangedRB.setEnabled(true);

				this.mtcRandomTotalRB.setEnabled(true);
				this.mtcRandomTypeRB.setEnabled(true);
				this.mtcUnchangedRB.setEnabled(true);
				this.mtcFullRB.setEnabled(true);
			} else {
				this.mtCompatPanel.setVisible(false);
				this.mtMovesPanel.setVisible(false);
				this.mtNoExistLabel.setVisible(true);
			}

			this.igtUnchangedRB.setEnabled(true);
			this.igtBothRB.setEnabled(true);
			this.igtGivenOnlyRB.setEnabled(true);

			if (this.romHandler instanceof Gen1RomHandler) {
				this.igtRandomItemCB.setVisible(false);
				this.igtRandomIVsCB.setVisible(false);
				this.igtRandomOTCB.setVisible(false);
			}

			this.fiUnchangedRB.setEnabled(true);
			this.fiRandomRB.setEnabled(true);
			this.fiShuffleRB.setEnabled(true);

			this.fiBanBadCB.setEnabled(false);
			this.fiBanBadCB.setSelected(false);

			if (this.romHandler instanceof AbstractDSRomHandler) {
				((AbstractDSRomHandler) this.romHandler).closeInnerRom();
			}
		} catch (Exception ex) {
			long time = System.currentTimeMillis();
			try {
				String errlog = "error_" + time + ".txt";
				PrintStream ps = new PrintStream(new FileOutputStream(errlog));
				PrintStream e1 = System.err;
				System.setErr(ps);
				ex.printStackTrace();
				System.setErr(e1);
				ps.close();
				JOptionPane
						.showMessageDialog(
								RandomizerGUI.this,
								String.format(
										bundle.getString("RandomizerGUI.processFailed"),
										errlog));
			} catch (Exception logex) {
				JOptionPane.showMessageDialog(RandomizerGUI.this,
						bundle.getString("RandomizerGUI.processFailedNoLog"));
			}
		}
	}

	private void populateDropdowns() {
		List<Pokemon> currentStarters = romHandler.getStarters();
		List<Pokemon> allPokes = romHandler.getPokemon();
		String[] pokeNames = new String[allPokes.size() - 1];
		for (int i = 1; i < allPokes.size(); i++) {
			pokeNames[i - 1] = allPokes.get(i).name;
		}
		this.spCustomPoke1Chooser.setModel(new DefaultComboBoxModel(pokeNames));
		this.spCustomPoke1Chooser.setSelectedIndex(allPokes
				.indexOf(currentStarters.get(0)) - 1);
		this.spCustomPoke2Chooser.setModel(new DefaultComboBoxModel(pokeNames));
		this.spCustomPoke2Chooser.setSelectedIndex(allPokes
				.indexOf(currentStarters.get(1)) - 1);
		if (!romHandler.isYellow()) {
			this.spCustomPoke3Chooser.setModel(new DefaultComboBoxModel(
					pokeNames));
			this.spCustomPoke3Chooser.setSelectedIndex(allPokes
					.indexOf(currentStarters.get(2)) - 1);
		}
	}

	private void enableOrDisableSubControls() {
		// This isn't for a new ROM being loaded (that's romLoaded)
		// This is just for when a radio button gets selected or state is loaded
		// and we need to enable/disable secondary controls
		// e.g. wild pokemon / trainer pokemon "modifier"
		// and the 3 starter pokemon dropdowns

		if (this.goUpdateMovesCheckBox.isSelected()
				&& !(romHandler instanceof Gen5RomHandler)) {
			this.goUpdateMovesLegacyCheckBox.setEnabled(true);
		} else {
			this.goUpdateMovesLegacyCheckBox.setEnabled(false);
			this.goUpdateMovesLegacyCheckBox.setSelected(false);
		}

		this.codeTweaksBtn.setEnabled(this.codeTweaksCB.isSelected());
		updateCodeTweaksButtonText();
		this.pokeLimitBtn.setEnabled(this.pokeLimitCB.isSelected());

		if (this.spCustomRB.isSelected()) {
			this.spCustomPoke1Chooser.setEnabled(true);
			this.spCustomPoke2Chooser.setEnabled(true);
			this.spCustomPoke3Chooser.setEnabled(true);
		} else {
			this.spCustomPoke1Chooser.setEnabled(false);
			this.spCustomPoke2Chooser.setEnabled(false);
			this.spCustomPoke3Chooser.setEnabled(false);
		}

		if (this.spHeldItemsCB.isSelected() && this.spHeldItemsCB.isVisible()
				&& this.spHeldItemsCB.isEnabled()) {
			this.spHeldItemsBanBadCB.setEnabled(true);
		} else {
			this.spHeldItemsBanBadCB.setEnabled(false);
			this.spHeldItemsBanBadCB.setSelected(false);
		}

		if (this.paRandomizeRB.isSelected()) {
			this.paWonderGuardCB.setEnabled(true);
		} else {
			this.paWonderGuardCB.setEnabled(false);
			this.paWonderGuardCB.setSelected(false);
		}

		if (this.tpUnchangedRB.isSelected()) {
			this.tpPowerLevelsCB.setEnabled(false);
			this.tpRivalCarriesStarterCB.setEnabled(false);
			this.tpNoLegendariesCB.setEnabled(false);
			this.tpNoEarlyShedinjaCB.setEnabled(false);
			this.tpNoEarlyShedinjaCB.setSelected(false);
		} else {
			this.tpPowerLevelsCB.setEnabled(true);
			this.tpRivalCarriesStarterCB.setEnabled(true);
			this.tpNoLegendariesCB.setEnabled(true);
			this.tpNoEarlyShedinjaCB.setEnabled(true);
		}

		if (this.tpTypeThemedRB.isSelected()) {
			this.tpTypeWeightingCB.setEnabled(true);
		} else {
			this.tpTypeWeightingCB.setEnabled(false);
		}

		if (this.wpArea11RB.isSelected() || this.wpRandomRB.isSelected()) {
			this.wpARNoneRB.setEnabled(true);
			this.wpARSimilarStrengthRB.setEnabled(true);
			this.wpARCatchEmAllRB.setEnabled(true);
			this.wpARTypeThemedRB.setEnabled(true);
		} else if (this.wpGlobalRB.isSelected()) {
			if (this.wpARCatchEmAllRB.isSelected()
					|| this.wpARTypeThemedRB.isSelected()) {
				this.wpARNoneRB.setSelected(true);
			}
			this.wpARNoneRB.setEnabled(true);
			this.wpARSimilarStrengthRB.setEnabled(true);
			this.wpARCatchEmAllRB.setEnabled(false);
			this.wpARTypeThemedRB.setEnabled(false);
		} else {
			this.wpARNoneRB.setEnabled(false);
			this.wpARSimilarStrengthRB.setEnabled(false);
			this.wpARCatchEmAllRB.setEnabled(false);
			this.wpARTypeThemedRB.setEnabled(false);
			this.wpARNoneRB.setSelected(true);
		}

		if (this.wpUnchangedRB.isSelected()) {
			this.wpUseTimeCB.setEnabled(false);
			this.wpNoLegendariesCB.setEnabled(false);
		} else {
			this.wpUseTimeCB.setEnabled(true);
			this.wpNoLegendariesCB.setEnabled(true);
		}

		if (this.wpHeldItemsCB.isSelected() && this.wpHeldItemsCB.isVisible()
				&& this.wpHeldItemsCB.isEnabled()) {
			this.wpHeldItemsBanBadCB.setEnabled(true);
		} else {
			this.wpHeldItemsBanBadCB.setEnabled(false);
			this.wpHeldItemsBanBadCB.setSelected(false);
		}

		if (this.igtUnchangedRB.isSelected()) {
			this.igtRandomItemCB.setEnabled(false);
			this.igtRandomIVsCB.setEnabled(false);
			this.igtRandomNicknameCB.setEnabled(false);
			this.igtRandomOTCB.setEnabled(false);
		} else {
			this.igtRandomItemCB.setEnabled(true);
			this.igtRandomIVsCB.setEnabled(true);
			this.igtRandomNicknameCB.setEnabled(true);
			this.igtRandomOTCB.setEnabled(true);
		}

		if (this.pmsMetronomeOnlyRB.isSelected()) {
			this.tmmUnchangedRB.setEnabled(false);
			this.tmmRandomRB.setEnabled(false);
			this.tmmUnchangedRB.setSelected(true);

			this.mtmUnchangedRB.setEnabled(false);
			this.mtmRandomRB.setEnabled(false);
			this.mtmUnchangedRB.setSelected(true);

			this.tmLearningSanityCB.setEnabled(false);
			this.tmLearningSanityCB.setSelected(false);
			this.tmKeepFieldMovesCB.setEnabled(false);
			this.tmKeepFieldMovesCB.setSelected(false);

			this.mtLearningSanityCB.setEnabled(false);
			this.mtLearningSanityCB.setSelected(false);
			this.mtKeepFieldMovesCB.setEnabled(false);
			this.mtKeepFieldMovesCB.setSelected(false);
		} else {
			this.tmmUnchangedRB.setEnabled(true);
			this.tmmRandomRB.setEnabled(true);

			this.mtmUnchangedRB.setEnabled(true);
			this.mtmRandomRB.setEnabled(true);

			if (!(this.pmsUnchangedRB.isSelected())
					|| !(this.tmmUnchangedRB.isSelected())
					|| !(this.thcUnchangedRB.isSelected())) {
				this.tmLearningSanityCB.setEnabled(true);
			} else {
				this.tmLearningSanityCB.setEnabled(false);
				this.tmLearningSanityCB.setSelected(false);
			}

			if (!(this.tmmUnchangedRB.isSelected())) {
				this.tmKeepFieldMovesCB.setEnabled(true);
			} else {
				this.tmKeepFieldMovesCB.setEnabled(false);
				this.tmKeepFieldMovesCB.setSelected(false);
			}

			if (this.romHandler.hasMoveTutors()
					&& (!(this.pmsUnchangedRB.isSelected())
							|| !(this.mtmUnchangedRB.isSelected()) || !(this.mtcUnchangedRB
								.isSelected()))) {
				this.mtLearningSanityCB.setEnabled(true);
			} else {
				this.mtLearningSanityCB.setEnabled(false);
				this.mtLearningSanityCB.setSelected(false);
			}

			if (this.romHandler.hasMoveTutors()
					&& !(this.mtmUnchangedRB.isSelected())) {
				this.mtKeepFieldMovesCB.setEnabled(true);
			} else {
				this.mtKeepFieldMovesCB.setEnabled(false);
				this.mtKeepFieldMovesCB.setSelected(false);
			}
		}

		if (this.pmsMetronomeOnlyRB.isSelected()
				|| this.pmsUnchangedRB.isSelected()) {
			this.pms4MovesCB.setEnabled(false);
			this.pms4MovesCB.setSelected(false);
		} else {
			this.pms4MovesCB.setEnabled(true);
		}

		if (this.fiRandomRB.isSelected() && this.fiRandomRB.isVisible()
				&& this.fiRandomRB.isEnabled()) {
			this.fiBanBadCB.setEnabled(true);
		} else {
			this.fiBanBadCB.setEnabled(false);
			this.fiBanBadCB.setSelected(false);
		}
	}

	private void saveROM() {
		if (romHandler == null) {
			return; // none loaded
		}
		if (raceModeCB.isSelected() && tpUnchangedRB.isSelected()
				&& wpUnchangedRB.isSelected()) {
			JOptionPane.showMessageDialog(this,
					bundle.getString("RandomizerGUI.raceModeRequirements"));
			return;
		}
		if (pokeLimitCB.isSelected()
				&& (this.currentRestrictions == null || this.currentRestrictions
						.nothingSelected())) {
			JOptionPane.showMessageDialog(this,
					bundle.getString("RandomizerGUI.pokeLimitNotChosen"));
			return;
		}
		romSaveChooser.setSelectedFile(null);
		int returnVal = romSaveChooser.showSaveDialog(this);
		if (returnVal == JFileChooser.APPROVE_OPTION) {
			File fh = romSaveChooser.getSelectedFile();
			// Fix or add extension
			List<String> extensions = new ArrayList<String>(Arrays.asList(
					"sgb", "gbc", "gba", "nds"));
			extensions.remove(this.romHandler.getDefaultExtension());
			fh = FileFunctions.fixFilename(fh,
					this.romHandler.getDefaultExtension(), extensions);
			boolean allowed = true;
			if (this.romHandler instanceof AbstractDSRomHandler) {
				String currentFN = this.romHandler.loadedFilename();
				if (currentFN.equals(fh.getAbsolutePath())) {
					JOptionPane.showMessageDialog(this,
							bundle.getString("RandomizerGUI.cantOverwriteDS"));
					allowed = false;
				}
			}
			if (allowed) {
				// Get a seed
				long seed = RandomSource.pickSeed();
				// Apply it
				RandomSource.seed(seed);
				presetMode = false;
				performRandomization(fh.getAbsolutePath(), seed, null, null,
						null);
			}
		}
	}

	private String getConfigString() {
		byte[] trainerClasses = null;
		byte[] trainerNames = null;
		byte[] nicknames = null;

		try {
			trainerClasses = FileFunctions
					.getConfigAsBytes("trainerclasses.txt");
			trainerNames = FileFunctions.getConfigAsBytes("trainernames.txt");
			nicknames = FileFunctions.getConfigAsBytes("nicknames.txt");
		} catch (IOException e) {
		}

		Settings settings = createSettingsFromState(trainerClasses,
				trainerNames, nicknames);
		return settings.toString();
	}

	public String getValidRequiredROMName(String config, byte[] trainerClasses,
			byte[] trainerNames, byte[] nicknames)
			throws UnsupportedEncodingException,
			InvalidSupplementFilesException {
		try {
			Utils.validatePresetSupplementFiles(config, trainerClasses,
					trainerNames, nicknames);
		} catch (InvalidSupplementFilesException e) {
			switch (e.getType()) {
			case TRAINER_CLASSES:
				JOptionPane.showMessageDialog(null, bundle
						.getString("RandomizerGUI.presetFailTrainerClasses"));
				throw e;
			case TRAINER_NAMES:
				JOptionPane.showMessageDialog(null, bundle
						.getString("RandomizerGUI.presetFailTrainerNames"));
				throw e;
			case NICKNAMES:
				JOptionPane.showMessageDialog(null,
						bundle.getString("RandomizerGUI.presetFailNicknames"));
				throw e;
			default:
				throw e;
			}
		}
		byte[] data = DatatypeConverter.parseBase64Binary(config);

		int nameLength = data[Settings.LENGTH_OF_SETTINGS_DATA] & 0xFF;
		if (data.length != Settings.LENGTH_OF_SETTINGS_DATA + 17 + nameLength) {
			return null; // not valid length
		}
		String name = new String(data, Settings.LENGTH_OF_SETTINGS_DATA + 1,
				nameLength, "US-ASCII");
		return name;
	}

	private void restoreStateFromSettings(Settings settings) {

		this.goLowerCaseNamesCheckBox.setSelected(settings
				.isLowerCasePokemonNames());
		this.goNationalDexCheckBox.setSelected(settings.isNationalDexAtStart());
		this.goRemoveTradeEvosCheckBox.setSelected(settings
				.isChangeImpossibleEvolutions());
		this.goUpdateMovesCheckBox.setSelected(settings.isUpdateMoves());
		this.goUpdateMovesLegacyCheckBox.setSelected(settings
				.isUpdateMovesLegacy());
		this.goUpdateTypesCheckBox.setSelected(settings
				.isUpdateTypeEffectiveness());
		this.tnRandomizeCB.setSelected(settings.isRandomizeTrainerNames());
		this.tcnRandomizeCB
				.setSelected(settings.isRandomizeTrainerClassNames());

		this.pbsChangesRandomEvosRB
				.setSelected(settings.getBaseStatisticsMod() == Settings.BaseStatisticsMod.RANDOM_FOLLOW_EVOLUTIONS);
		this.pbsChangesRandomTotalRB
				.setSelected(settings.getBaseStatisticsMod() == Settings.BaseStatisticsMod.COMPLETELY_RANDOM);
		this.pbsChangesShuffleRB
				.setSelected(settings.getBaseStatisticsMod() == Settings.BaseStatisticsMod.SHUFFLE);
		this.pbsChangesUnchangedRB
				.setSelected(settings.getBaseStatisticsMod() == Settings.BaseStatisticsMod.UNCHANGED);
		this.paUnchangedRB
				.setSelected(settings.getAbilitiesMod() == Settings.AbilitiesMod.UNCHANGED);
		this.paRandomizeRB
				.setSelected(settings.getAbilitiesMod() == Settings.AbilitiesMod.RANDOMIZE);
		this.paWonderGuardCB.setSelected(settings.isAllowWonderGuard());
		this.pbsStandardEXPCurvesCB.setSelected(settings
				.isStandardizeEXPCurves());

		this.ptRandomFollowEvosRB
				.setSelected(settings.getTypesMod() == Settings.TypesMod.RANDOM_FOLLOW_EVOLUTIONS);
		this.ptRandomTotalRB
				.setSelected(settings.getTypesMod() == Settings.TypesMod.COMPLETELY_RANDOM);
		this.ptUnchangedRB
				.setSelected(settings.getTypesMod() == Settings.TypesMod.UNCHANGED);
		this.codeTweaksCB.setSelected(settings.isUseCodeTweaks());
		this.raceModeCB.setSelected(settings.isRaceMode());
		this.randomizeHollowsCB
				.setSelected(settings.isRandomizeHiddenHollows());
		this.brokenMovesCB.setSelected(settings.doBlockBrokenMoves());
		this.pokeLimitCB.setSelected(settings.isLimitPokemon());

		this.goCondenseEvosCheckBox.setSelected(settings
				.isMakeEvolutionsEasier());

		this.spCustomRB
				.setSelected(settings.getStartersMod() == Settings.StartersMod.CUSTOM);
		this.spRandomRB
				.setSelected(settings.getStartersMod() == Settings.StartersMod.COMPLETELY_RANDOM);
		this.spUnchangedRB
				.setSelected(settings.getStartersMod() == Settings.StartersMod.UNCHANGED);
		this.spRandom2EvosRB
				.setSelected(settings.getStartersMod() == Settings.StartersMod.RANDOM_WITH_TWO_EVOLUTIONS);
		this.spHeldItemsCB.setSelected(settings.isRandomizeStartersHeldItems());
		this.spHeldItemsBanBadCB.setSelected(settings
				.isBanBadRandomStarterHeldItems());

		int[] customStarters = settings.getCustomStarters();
		this.spCustomPoke1Chooser.setSelectedIndex(customStarters[0] - 1);
		this.spCustomPoke2Chooser.setSelectedIndex(customStarters[1] - 1);
		this.spCustomPoke3Chooser.setSelectedIndex(customStarters[2] - 1);

		this.pmsRandomTotalRB
				.setSelected(settings.getMovesetsMod() == Settings.MovesetsMod.COMPLETELY_RANDOM);
		this.pmsRandomTypeRB
				.setSelected(settings.getMovesetsMod() == Settings.MovesetsMod.RANDOM_PREFER_SAME_TYPE);
		this.pmsUnchangedRB
				.setSelected(settings.getMovesetsMod() == Settings.MovesetsMod.UNCHANGED);
		this.pmsMetronomeOnlyRB
				.setSelected(settings.getMovesetsMod() == Settings.MovesetsMod.METRONOME_ONLY);
		this.pms4MovesCB.setSelected(settings.isStartWithFourMoves());

		this.tpPowerLevelsCB.setSelected(settings
				.isTrainersUsePokemonOfSimilarStrength());
		this.tpRandomRB
				.setSelected(settings.getTrainersMod() == Settings.TrainersMod.RANDOM);
		this.tpRivalCarriesStarterCB.setSelected(settings
				.isRivalCarriesStarterThroughout());
		this.tpTypeThemedRB
				.setSelected(settings.getTrainersMod() == Settings.TrainersMod.TYPE_THEMED);
		this.tpTypeWeightingCB.setSelected(settings
				.isTrainersMatchTypingDistribution());
		this.tpUnchangedRB
				.setSelected(settings.getTrainersMod() == Settings.TrainersMod.UNCHANGED);
		this.tpNoLegendariesCB.setSelected(settings
				.isTrainersBlockLegendaries());
		this.tpNoEarlyShedinjaCB.setSelected(settings
				.isTrainersBlockEarlyWonderGuard());

		this.wpARCatchEmAllRB
				.setSelected(settings.getWildPokemonRestrictionMod() == Settings.WildPokemonRestrictionMod.CATCH_EM_ALL);
		this.wpArea11RB
				.setSelected(settings.getWildPokemonMod() == Settings.WildPokemonMod.AREA_MAPPING);
		this.wpARNoneRB
				.setSelected(settings.getWildPokemonRestrictionMod() == Settings.WildPokemonRestrictionMod.NONE);
		this.wpARTypeThemedRB
				.setSelected(settings.getWildPokemonRestrictionMod() == Settings.WildPokemonRestrictionMod.TYPE_THEME_AREAS);
		this.wpGlobalRB
				.setSelected(settings.getWildPokemonMod() == Settings.WildPokemonMod.GLOBAL_MAPPING);
		this.wpRandomRB
				.setSelected(settings.getWildPokemonMod() == Settings.WildPokemonMod.RANDOM);
		this.wpUnchangedRB
				.setSelected(settings.getWildPokemonMod() == Settings.WildPokemonMod.UNCHANGED);
		this.wpUseTimeCB.setSelected(settings.isUseTimeBasedEncounters());

		this.wpCatchRateCB.setSelected(settings.isUseMinimumCatchRate());
		this.wpNoLegendariesCB.setSelected(settings.isBlockWildLegendaries());
		this.wpARSimilarStrengthRB
				.setSelected(settings.getWildPokemonRestrictionMod() == Settings.WildPokemonRestrictionMod.SIMILAR_STRENGTH);
		this.wpHeldItemsCB.setSelected(settings
				.isRandomizeWildPokemonHeldItems());
		this.wpHeldItemsBanBadCB.setSelected(settings
				.isBanBadRandomWildPokemonHeldItems());

		this.stpUnchangedRB
				.setSelected(settings.getStaticPokemonMod() == Settings.StaticPokemonMod.UNCHANGED);
		this.stpRandomL4LRB
				.setSelected(settings.getStaticPokemonMod() == Settings.StaticPokemonMod.RANDOM_MATCHING);
		this.stpRandomTotalRB
				.setSelected(settings.getStaticPokemonMod() == Settings.StaticPokemonMod.COMPLETELY_RANDOM);

		this.thcRandomTotalRB
				.setSelected(settings.getTmsHmsCompatibilityMod() == Settings.TMsHMsCompatibilityMod.COMPLETELY_RANDOM);
		this.thcRandomTypeRB
				.setSelected(settings.getTmsHmsCompatibilityMod() == Settings.TMsHMsCompatibilityMod.RANDOM_PREFER_TYPE);
		this.thcUnchangedRB
				.setSelected(settings.getTmsHmsCompatibilityMod() == Settings.TMsHMsCompatibilityMod.UNCHANGED);
		this.tmmRandomRB
				.setSelected(settings.getTmsMod() == Settings.TMsMod.RANDOM);
		this.tmmUnchangedRB
				.setSelected(settings.getTmsMod() == Settings.TMsMod.UNCHANGED);
		this.tmLearningSanityCB.setSelected(settings.isTmLevelUpMoveSanity());
		this.tmKeepFieldMovesCB.setSelected(settings.isKeepFieldMoveTMs());
		this.thcFullRB
				.setSelected(settings.getTmsHmsCompatibilityMod() == Settings.TMsHMsCompatibilityMod.FULL);

		this.mtcRandomTotalRB
				.setSelected(settings.getMoveTutorsCompatibilityMod() == Settings.MoveTutorsCompatibilityMod.COMPLETELY_RANDOM);
		this.mtcRandomTypeRB
				.setSelected(settings.getMoveTutorsCompatibilityMod() == Settings.MoveTutorsCompatibilityMod.RANDOM_PREFER_TYPE);
		this.mtcUnchangedRB
				.setSelected(settings.getMoveTutorsCompatibilityMod() == Settings.MoveTutorsCompatibilityMod.UNCHANGED);
		this.mtmRandomRB
				.setSelected(settings.getMoveTutorMovesMod() == Settings.MoveTutorMovesMod.RANDOM);
		this.mtmUnchangedRB
				.setSelected(settings.getMoveTutorMovesMod() == Settings.MoveTutorMovesMod.UNCHANGED);
		this.mtLearningSanityCB
				.setSelected(settings.isTutorLevelUpMoveSanity());
		this.mtKeepFieldMovesCB.setSelected(settings.isKeepFieldMoveTutors());
		this.mtcFullRB
				.setSelected(settings.getMoveTutorsCompatibilityMod() == Settings.MoveTutorsCompatibilityMod.FULL);

		this.igtBothRB
				.setSelected(settings.getInGameTradesMod() == Settings.InGameTradesMod.RANDOMIZE_GIVEN_AND_REQUESTED);
		this.igtGivenOnlyRB
				.setSelected(settings.getInGameTradesMod() == Settings.InGameTradesMod.RANDOMIZE_GIVEN);
		this.igtRandomItemCB.setSelected(settings
				.isRandomizeInGameTradesItems());
		this.igtRandomIVsCB.setSelected(settings.isRandomizeInGameTradesIVs());
		this.igtRandomNicknameCB.setSelected(settings
				.isRandomizeInGameTradesNicknames());
		this.igtRandomOTCB.setSelected(settings.isRandomizeInGameTradesOTs());
		this.igtUnchangedRB
				.setSelected(settings.getInGameTradesMod() == Settings.InGameTradesMod.UNCHANGED);

		this.fiRandomRB
				.setSelected(settings.getFieldItemsMod() == Settings.FieldItemsMod.RANDOM);
		this.fiShuffleRB
				.setSelected(settings.getFieldItemsMod() == Settings.FieldItemsMod.SHUFFLE);
		this.fiUnchangedRB
				.setSelected(settings.getFieldItemsMod() == Settings.FieldItemsMod.UNCHANGED);
		this.fiBanBadCB.setSelected(settings.isBanBadRandomFieldItems());

		this.currentRestrictions = settings.getCurrentRestrictions();
		if (this.currentRestrictions != null) {
			this.currentRestrictions.limitToGen(this.romHandler
					.generationOfPokemon());
		}
		this.currentCodeTweaks = settings.getCurrentCodeTweaks();
		updateCodeTweaksButtonText();

		this.enableOrDisableSubControls();
	}

	private Settings createSettingsFromState(byte[] trainerClasses,
			byte[] trainerNames, byte[] nicknames) {
		Settings settings = new Settings();
		settings.setRomName(this.romHandler.getROMName());
		settings.setLowerCasePokemonNames(goLowerCaseNamesCheckBox.isSelected());
		settings.setNationalDexAtStart(goNationalDexCheckBox.isSelected());
		settings.setChangeImpossibleEvolutions(goRemoveTradeEvosCheckBox
				.isSelected());
		settings.setUpdateMoves(goUpdateMovesCheckBox.isSelected());
		settings.setUpdateMovesLegacy(goUpdateMovesLegacyCheckBox.isSelected());
		settings.setUpdateTypeEffectiveness(goUpdateTypesCheckBox.isSelected());
		settings.setRandomizeTrainerNames(tnRandomizeCB.isSelected());
		settings.setRandomizeTrainerClassNames(tcnRandomizeCB.isSelected());

		settings.setBaseStatisticsMod(pbsChangesUnchangedRB.isSelected(),
				pbsChangesShuffleRB.isSelected(),
				pbsChangesRandomEvosRB.isSelected(),
				pbsChangesRandomTotalRB.isSelected());
		settings.setAbilitiesMod(paUnchangedRB.isSelected(),
				paRandomizeRB.isSelected());
		settings.setAllowWonderGuard(paWonderGuardCB.isSelected());
		settings.setStandardizeEXPCurves(pbsStandardEXPCurvesCB.isSelected());

		settings.setTypesMod(ptUnchangedRB.isSelected(),
				ptRandomFollowEvosRB.isSelected(), ptRandomTotalRB.isSelected());
		settings.setUseCodeTweaks(codeTweaksCB.isSelected());
		settings.setRaceMode(raceModeCB.isSelected());
		settings.setRandomizeHiddenHollows(randomizeHollowsCB.isSelected());
		settings.setBlockBrokenMoves(brokenMovesCB.isSelected());
		settings.setLimitPokemon(pokeLimitCB.isSelected());

		settings.setMakeEvolutionsEasier(goCondenseEvosCheckBox.isSelected());

		settings.setStartersMod(spUnchangedRB.isSelected(),
				spCustomRB.isSelected(), spRandomRB.isSelected(),
				spRandom2EvosRB.isSelected());
		settings.setRandomizeStartersHeldItems(spHeldItemsCB.isSelected());
		settings.setBanBadRandomStarterHeldItems(spHeldItemsBanBadCB
				.isSelected());

		int[] customStarters = new int[] {
				spCustomPoke1Chooser.getSelectedIndex() + 1,
				spCustomPoke2Chooser.getSelectedIndex() + 1,
				spCustomPoke3Chooser.getSelectedIndex() + 1 };
		settings.setCustomStarters(customStarters);

		settings.setMovesetsMod(pmsUnchangedRB.isSelected(),
				pmsRandomTypeRB.isSelected(), pmsRandomTotalRB.isSelected(),
				pmsMetronomeOnlyRB.isSelected());
		settings.setStartWithFourMoves(pms4MovesCB.isSelected());

		settings.setTrainersMod(tpUnchangedRB.isSelected(),
				tpRandomRB.isSelected(), tpTypeThemedRB.isSelected());
		settings.setTrainersUsePokemonOfSimilarStrength(tpPowerLevelsCB
				.isSelected());
		settings.setRivalCarriesStarterThroughout(tpRivalCarriesStarterCB
				.isSelected());
		settings.setTrainersMatchTypingDistribution(tpTypeWeightingCB
				.isSelected());
		settings.setTrainersBlockLegendaries(tpNoLegendariesCB.isSelected());
		settings.setTrainersBlockEarlyWonderGuard(tpNoEarlyShedinjaCB
				.isSelected());

		settings.setWildPokemonMod(wpUnchangedRB.isSelected(),
				wpRandomRB.isSelected(), wpArea11RB.isSelected(),
				wpGlobalRB.isSelected());
		settings.setWildPokemonRestrictionMod(wpARNoneRB.isSelected(),
				wpARSimilarStrengthRB.isSelected(),
				wpARCatchEmAllRB.isSelected(), wpARTypeThemedRB.isSelected());
		settings.setUseTimeBasedEncounters(wpUseTimeCB.isSelected());
		settings.setUseMinimumCatchRate(wpCatchRateCB.isSelected());
		settings.setBlockWildLegendaries(wpNoLegendariesCB.isSelected());
		settings.setRandomizeWildPokemonHeldItems(wpHeldItemsCB.isSelected());
		settings.setBanBadRandomWildPokemonHeldItems(wpHeldItemsBanBadCB
				.isSelected());

		settings.setStaticPokemonMod(stpUnchangedRB.isSelected(),
				stpRandomL4LRB.isSelected(), stpRandomTotalRB.isSelected());

		settings.setTmsMod(tmmUnchangedRB.isSelected(),
				tmmRandomRB.isSelected());

		settings.setTmsHmsCompatibilityMod(thcUnchangedRB.isSelected(),
				thcRandomTypeRB.isSelected(), thcRandomTotalRB.isSelected(),
				thcFullRB.isSelected());
		settings.setTmLevelUpMoveSanity(tmLearningSanityCB.isSelected());
		settings.setKeepFieldMoveTMs(tmKeepFieldMovesCB.isSelected());

		settings.setMoveTutorMovesMod(mtmUnchangedRB.isSelected(),
				mtmRandomRB.isSelected());
		settings.setMoveTutorsCompatibilityMod(mtcUnchangedRB.isSelected(),
				mtcRandomTypeRB.isSelected(), mtcRandomTotalRB.isSelected(),
				mtcFullRB.isSelected());
		settings.setTutorLevelUpMoveSanity(mtLearningSanityCB.isSelected());
		settings.setKeepFieldMoveTutors(mtKeepFieldMovesCB.isSelected());

		settings.setInGameTradesMod(igtUnchangedRB.isSelected(),
				igtGivenOnlyRB.isSelected(), igtBothRB.isSelected());
		settings.setRandomizeInGameTradesItems(igtRandomItemCB.isSelected());
		settings.setRandomizeInGameTradesIVs(igtRandomIVsCB.isSelected());
		settings.setRandomizeInGameTradesNicknames(igtRandomNicknameCB
				.isSelected());
		settings.setRandomizeInGameTradesOTs(igtRandomOTCB.isSelected());

		settings.setFieldItemsMod(fiUnchangedRB.isSelected(),
				fiShuffleRB.isSelected(), fiRandomRB.isSelected());
		settings.setBanBadRandomFieldItems(fiBanBadCB.isSelected());

		settings.setCurrentRestrictions(currentRestrictions);
		settings.setCurrentCodeTweaks(currentCodeTweaks);

		settings.setTrainerNames(trainerNames);
		settings.setTrainerClasses(trainerClasses);
		settings.setNicknames(nicknames);

		return settings;
	}

	private void performRandomization(final String filename, final long seed,
			byte[] trainerClasses, byte[] trainerNames, byte[] nicknames) {
		final Settings settings = createSettingsFromState(trainerClasses,
				trainerNames, nicknames);
		final boolean raceMode = settings.isRaceMode();
		// Setup verbose log
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			verboseLog = new PrintStream(baos, false, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			verboseLog = new PrintStream(baos);
		}

		try {
			final AtomicInteger finishedCV = new AtomicInteger(0);
			opDialog = new OperationDialog(
					bundle.getString("RandomizerGUI.savingText"), this, true);
			Thread t = new Thread() {
				@Override
				public void run() {
					SwingUtilities.invokeLater(new Runnable() {
						@Override
						public void run() {
							opDialog.setVisible(true);
						}
					});
					boolean succeededSave = false;
					try {
						finishedCV.set(new Randomizer(settings,
								RandomizerGUI.this.romHandler).randomize(
								filename, verboseLog, seed));
						succeededSave = true;
					} catch (Exception ex) {
						long time = System.currentTimeMillis();
						try {
							String errlog = "error_" + time + ".txt";
							PrintStream ps = new PrintStream(
									new FileOutputStream(errlog));
							PrintStream e1 = System.err;
							System.setErr(ps);
							ex.printStackTrace();
							verboseLog.close();
							System.setErr(e1);
							ps.close();
							JOptionPane
									.showMessageDialog(
											RandomizerGUI.this,
											String.format(
													bundle.getString("RandomizerGUI.saveFailedIO"),
													errlog));
						} catch (Exception logex) {
							JOptionPane
									.showMessageDialog(
											RandomizerGUI.this,
											bundle.getString("RandomizerGUI.saveFailedIONoLog"));
							verboseLog.close();
						}
					}
					if (succeededSave) {
						SwingUtilities.invokeLater(new Runnable() {
							@Override
							public void run() {
								RandomizerGUI.this.opDialog.setVisible(false);
								// Log?
								verboseLog.close();
								byte[] out = baos.toByteArray();
								verboseLog = System.out;

								if (raceMode) {
									JOptionPane.showMessageDialog(
											RandomizerGUI.this,
											String.format(
													bundle.getString("RandomizerGUI.raceModeCheckValuePopup"),
													finishedCV.get()));
								} else {
									int response = JOptionPane.showConfirmDialog(
											RandomizerGUI.this,
											bundle.getString("RandomizerGUI.saveLogDialog.text"),
											bundle.getString("RandomizerGUI.saveLogDialog.title"),
											JOptionPane.YES_NO_OPTION);
									if (response == JOptionPane.YES_OPTION) {
										try {
											FileOutputStream fos = new FileOutputStream(
													filename + ".log");
											fos.write(0xEF);
											fos.write(0xBB);
											fos.write(0xBF);
											fos.write(out);
											fos.close();
										} catch (IOException e) {
											JOptionPane.showMessageDialog(
													RandomizerGUI.this,
													bundle.getString("RandomizerGUI.logSaveFailed"));
											return;
										}
										JOptionPane.showMessageDialog(
												RandomizerGUI.this,
												String.format(
														bundle.getString("RandomizerGUI.logSaved"),
														filename));
									}
								}
								if (presetMode) {
									JOptionPane.showMessageDialog(
											RandomizerGUI.this,
											bundle.getString("RandomizerGUI.randomizationDone"));
									// Done
									RandomizerGUI.this.romHandler = null;
									initialFormState();
								} else {
									// Compile a config string
									String configString = getConfigString();
									// Show the preset maker
									new PresetMakeDialog(RandomizerGUI.this,
											seed, configString);

									// Done
									RandomizerGUI.this.romHandler = null;
									initialFormState();
								}
							}
						});
					} else {
						SwingUtilities.invokeLater(new Runnable() {
							@Override
							public void run() {
								RandomizerGUI.this.opDialog.setVisible(false);
								verboseLog = System.out;
								RandomizerGUI.this.romHandler = null;
								initialFormState();
							}
						});
					}
				}
			};
			t.start();
		} catch (Exception ex) {
			long time = System.currentTimeMillis();
			try {
				String errlog = "error_" + time + ".txt";
				PrintStream ps = new PrintStream(new FileOutputStream(errlog));
				PrintStream e1 = System.err;
				System.setErr(ps);
				ex.printStackTrace();
				verboseLog.close();
				byte[] out = baos.toByteArray();
				System.err.print(new String(out, "UTF-8"));
				System.setErr(e1);
				ps.close();
				JOptionPane.showMessageDialog(this, String.format(
						bundle.getString("RandomizerGUI.saveFailed"), errlog));
			} catch (Exception logex) {
				JOptionPane.showMessageDialog(this,
						bundle.getString("RandomizerGUI.saveFailedNoLog"));
				verboseLog.close();
			}
		}
	}

	private void presetLoader() {
		PresetLoadDialog pld = new PresetLoadDialog(this);
		if (pld.isCompleted()) {
			// Apply it
			long seed = pld.getSeed();
			String config = pld.getConfigString();
			this.romHandler = pld.getROM();
			this.romLoaded();
			Settings settings;
			try {
				settings = Settings.fromString(config);
				settings.tweakForRom(this.romHandler);
				this.restoreStateFromSettings(settings);
			} catch (UnsupportedEncodingException e) {
				// settings load failed
				// TODO better handling of this
				this.romHandler = null;
				initialFormState();
			}
			romSaveChooser.setSelectedFile(null);
			int returnVal = romSaveChooser.showSaveDialog(this);
			if (returnVal == JFileChooser.APPROVE_OPTION) {
				File fh = romSaveChooser.getSelectedFile();
				// Fix or add extension
				List<String> extensions = new ArrayList<String>(Arrays.asList(
						"sgb", "gbc", "gba", "nds"));
				extensions.remove(this.romHandler.getDefaultExtension());
				fh = FileFunctions.fixFilename(fh,
						this.romHandler.getDefaultExtension(), extensions);
				boolean allowed = true;
				if (this.romHandler instanceof AbstractDSRomHandler) {
					String currentFN = this.romHandler.loadedFilename();
					if (currentFN.equals(fh.getAbsolutePath())) {
						JOptionPane.showMessageDialog(this, bundle
								.getString("RandomizerGUI.cantOverwriteDS"));
						allowed = false;
					}
				}
				if (allowed) {
					// Apply the seed we were given
					RandomSource.seed(seed);
					presetMode = true;
					performRandomization(fh.getAbsolutePath(), seed,
							pld.getTrainerClasses(), pld.getTrainerNames(),
							pld.getNicknames());
				} else {
					this.romHandler = null;
					initialFormState();
				}

			} else {
				this.romHandler = null;
				initialFormState();
			}
		}

	}

	private void updateCodeTweaksButtonText() {
		if (currentCodeTweaks == 0 || !codeTweaksCB.isSelected()) {
			codeTweaksBtn.setText(bundle
					.getString("RandomizerGUI.codeTweaksBtn.text"));
		} else {
			int ctCount = 0;
			for (int i = 0; i < 32; i++) {
				if ((currentCodeTweaks & (1 << i)) > 0) {
					ctCount++;
				}
			}
			codeTweaksBtn.setText(String.format(bundle
					.getString("RandomizerGUI.codeTweaksBtn.textWithActive"),
					ctCount));
		}
	}

	// public response methods

	public void updateFound(int newVersion, String changelog) {
		new UpdateFoundDialog(this, newVersion, changelog);
	}

	public void noUpdateFound() {
		JOptionPane.showMessageDialog(this,
				bundle.getString("RandomizerGUI.noUpdates"));
	}

	public static String getRootPath() {
		return rootPath;
	}

	// actions

	private void updateSettingsButtonActionPerformed(
			java.awt.event.ActionEvent evt) {// GEN-FIRST:event_updateSettingsButtonActionPerformed
		if (autoUpdateEnabled) {
			toggleAutoUpdatesMenuItem.setText(bundle
					.getString("RandomizerGUI.disableAutoUpdate"));
		} else {
			toggleAutoUpdatesMenuItem.setText(bundle
					.getString("RandomizerGUI.enableAutoUpdate"));
		}
		updateSettingsMenu.show(updateSettingsButton, 0,
				updateSettingsButton.getHeight());
	}// GEN-LAST:event_updateSettingsButtonActionPerformed

	private void toggleAutoUpdatesMenuItemActionPerformed(
			java.awt.event.ActionEvent evt) {// GEN-FIRST:event_toggleAutoUpdatesMenuItemActionPerformed
		autoUpdateEnabled = !autoUpdateEnabled;
		if (autoUpdateEnabled) {
			JOptionPane.showMessageDialog(this,
					bundle.getString("RandomizerGUI.autoUpdateEnabled"));
		} else {
			JOptionPane.showMessageDialog(this,
					bundle.getString("RandomizerGUI.autoUpdateDisabled"));
		}
		attemptWriteConfig();
	}// GEN-LAST:event_toggleAutoUpdatesMenuItemActionPerformed

	private void manualUpdateMenuItemActionPerformed(
			java.awt.event.ActionEvent evt) {// GEN-FIRST:event_manualUpdateMenuItemActionPerformed
		new UpdateCheckThread(this, true).start();
	}// GEN-LAST:event_manualUpdateMenuItemActionPerformed

	private void loadQSButtonActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_loadQSButtonActionPerformed
		if (this.romHandler == null) {
			return;
		}
		qsOpenChooser.setSelectedFile(null);
		int returnVal = qsOpenChooser.showOpenDialog(this);
		if (returnVal == JFileChooser.APPROVE_OPTION) {
			File fh = qsOpenChooser.getSelectedFile();
			try {
				FileInputStream fis = new FileInputStream(fh);
				Settings settings = Settings.read(fis);
				fis.close();

				// load settings
				initialFormState();
				romLoaded();
				Settings.TweakForROMFeedback feedback = settings
						.tweakForRom(this.romHandler);
				if (feedback.isChangedStarter()
						&& settings.getStartersMod() == Settings.StartersMod.CUSTOM) {
					JOptionPane.showMessageDialog(this, bundle
							.getString("RandomizerGUI.starterUnavailable"));
				}
				this.restoreStateFromSettings(settings);

				if (settings.isUpdatedFromOldVersion()) {
					// show a warning dialog, but load it
					JOptionPane
							.showMessageDialog(
									this,
									bundle.getString("RandomizerGUI.settingsFileOlder"));
				}

				JOptionPane.showMessageDialog(this, String.format(
						bundle.getString("RandomizerGUI.settingsLoaded"),
						fh.getName()));
			} catch (UnsupportedOperationException ex) {
				JOptionPane.showMessageDialog(this,
						bundle.getString("RandomizerGUI.settingsFileNewer"));
			} catch (IllegalArgumentException ex) {
				JOptionPane.showMessageDialog(this,
						bundle.getString("RandomizerGUI.invalidSettingsFile"));
			} catch (IOException ex) {
				JOptionPane.showMessageDialog(this,
						bundle.getString("RandomizerGUI.settingsLoadFailed"));
			}
		}
	}// GEN-LAST:event_loadQSButtonActionPerformed

	private void saveQSButtonActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_saveQSButtonActionPerformed
		if (this.romHandler == null) {
			return;
		}
		qsSaveChooser.setSelectedFile(null);
		int returnVal = qsSaveChooser.showSaveDialog(this);
		if (returnVal == JFileChooser.APPROVE_OPTION) {
			File fh = qsSaveChooser.getSelectedFile();
			// Fix or add extension
			fh = FileFunctions.fixFilename(fh, "rnqs");
			// Save now?
			try {
				FileOutputStream fos = new FileOutputStream(fh);
				fos.write(Settings.VERSION);
				byte[] configString = getConfigString().getBytes("UTF-8");
				fos.write(configString.length);
				fos.write(configString);
				fos.close();
			} catch (IOException ex) {
				JOptionPane.showMessageDialog(this,
						bundle.getString("RandomizerGUI.settingsSaveFailed"));
			}
		}
	}// GEN-LAST:event_saveQSButtonActionPerformed

	private void codeTweaksBtnActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_codeTweaksBtnActionPerformed
		CodeTweaksDialog ctd = new CodeTweaksDialog(this,
				this.currentCodeTweaks, this.romHandler.codeTweaksAvailable());
		if (ctd.pressedOK()) {
			this.currentCodeTweaks = ctd.getChoice();
			updateCodeTweaksButtonText();
		}
	}// GEN-LAST:event_codeTweaksBtnActionPerformed

	private void pokeLimitBtnActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_pokeLimitBtnActionPerformed
		GenerationLimitDialog gld = new GenerationLimitDialog(this,
				this.currentRestrictions, this.romHandler.generationOfPokemon());
		if (gld.pressedOK()) {
			this.currentRestrictions = gld.getChoice();
		}
	}// GEN-LAST:event_pokeLimitBtnActionPerformed

	private void goUpdateMovesCheckBoxActionPerformed(
			java.awt.event.ActionEvent evt) {// GEN-FIRST:event_goUpdateMovesCheckBoxActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_goUpdateMovesCheckBoxActionPerformed

	private void codeTweaksCBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_codeTweaksCBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_codeTweaksCBActionPerformed

	private void pokeLimitCBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_pokeLimitCBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_pokeLimitCBActionPerformed

	private void pmsMetronomeOnlyRBActionPerformed(
			java.awt.event.ActionEvent evt) {// GEN-FIRST:event_pmsMetronomeOnlyRBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_pmsMetronomeOnlyRBActionPerformed

	private void igtUnchangedRBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_igtUnchangedRBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_igtUnchangedRBActionPerformed

	private void igtGivenOnlyRBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_igtGivenOnlyRBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_igtGivenOnlyRBActionPerformed

	private void igtBothRBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_igtBothRBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_igtBothRBActionPerformed

	private void wpARNoneRBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_wpARNoneRBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_wpARNoneRBActionPerformed

	private void wpARSimilarStrengthRBActionPerformed(
			java.awt.event.ActionEvent evt) {// GEN-FIRST:event_wpARSimilarStrengthRBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_wpARSimilarStrengthRBActionPerformed

	private void wpARCatchEmAllRBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_wpARCatchEmAllRBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_wpARCatchEmAllRBActionPerformed

	private void wpARTypeThemedRBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_wpARTypeThemedRBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_wpARTypeThemedRBActionPerformed

	private void pmsUnchangedRBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_pmsUnchangedRBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_pmsUnchangedRBActionPerformed

	private void pmsRandomTypeRBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_pmsRandomTypeRBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_pmsRandomTypeRBActionPerformed

	private void pmsRandomTotalRBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_pmsRandomTotalRBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_pmsRandomTotalRBActionPerformed

	private void mtmUnchangedRBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_mtmUnchangedRBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_mtmUnchangedRBActionPerformed

	private void paUnchangedRBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_paUnchangedRBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_paUnchangedRBActionPerformed

	private void paRandomizeRBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_paRandomizeRBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_paRandomizeRBActionPerformed

	private void aboutButtonActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_aboutButtonActionPerformed
		new AboutDialog(this, true).setVisible(true);
	}// GEN-LAST:event_aboutButtonActionPerformed

	private void openROMButtonActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_openROMButtonActionPerformed
		loadROM();
	}// GEN-LAST:event_openROMButtonActionPerformed

	private void saveROMButtonActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_saveROMButtonActionPerformed
		saveROM();
	}// GEN-LAST:event_saveROMButtonActionPerformed

	private void usePresetsButtonActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_usePresetsButtonActionPerformed
		presetLoader();
	}// GEN-LAST:event_usePresetsButtonActionPerformed

	private void wpUnchangedRBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_wpUnchangedRBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_wpUnchangedRBActionPerformed

	private void tpUnchangedRBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_tpUnchangedRBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_tpUnchangedRBActionPerformed

	private void tpRandomRBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_tpRandomRBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_tpRandomRBActionPerformed

	private void tpTypeThemedRBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_tpTypeThemedRBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_tpTypeThemedRBActionPerformed

	private void spUnchangedRBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_spUnchangedRBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_spUnchangedRBActionPerformed

	private void spCustomRBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_spCustomRBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_spCustomRBActionPerformed

	private void spRandomRBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_spRandomRBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_spRandomRBActionPerformed

	private void wpRandomRBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_wpRandomRBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_wpRandomRBActionPerformed

	private void wpArea11RBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_wpArea11RBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_wpArea11RBActionPerformed

	private void wpGlobalRBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_wpGlobalRBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_wpGlobalRBActionPerformed

	private void tmmUnchangedRBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_tmmUnchangedRBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_tmmUnchangedRBActionPerformed

	private void tmmRandomRBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_tmmRandomRBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_tmmRandomRBActionPerformed

	private void mtmRandomRBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_mtmRandomRBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_mtmRandomRBActionPerformed

	private void thcUnchangedRBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_thcUnchangedRBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_thcUnchangedRBActionPerformed

	private void thcRandomTypeRBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_thcRandomTypeRBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_thcRandomTypeRBActionPerformed

	private void thcRandomTotalRBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_thcRandomTotalRBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_thcRandomTotalRBActionPerformed

	private void mtcUnchangedRBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_mtcUnchangedRBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_mtcUnchangedRBActionPerformed

	private void mtcRandomTypeRBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_mtcRandomTypeRBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_mtcRandomTypeRBActionPerformed

	private void mtcRandomTotalRBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_mtcRandomTotalRBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_mtcRandomTotalRBActionPerformed

	private void thcFullRBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_thcFullRBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_thcFullRBActionPerformed

	private void mtcFullRBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_mtcFullRBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_mtcFullRBActionPerformed

	private void spHeldItemsCBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_spHeldItemsCBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_spHeldItemsCBActionPerformed

	private void wpHeldItemsCBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_wpHeldItemsCBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_wpHeldItemsCBActionPerformed

	private void fiUnchangedRBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_fiUnchangedRBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_fiUnchangedRBActionPerformed

	private void fiShuffleRBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_fiShuffleRBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_fiShuffleRBActionPerformed

	private void fiRandomRBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_fiRandomRBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_fiRandomRBActionPerformed

	private void spRandom2EvosRBActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_spRandom2EvosRBActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_spRandom2EvosRBActionPerformed

	private void goCondenseEvosCheckBoxActionPerformed(
			java.awt.event.ActionEvent evt) {// GEN-FIRST:event_goCondenseEvosCheckBoxActionPerformed
		this.enableOrDisableSubControls();
	}// GEN-LAST:event_goCondenseEvosCheckBoxActionPerformed

	/**
	 * This method is called from within the constructor to initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is always
	 * regenerated by the Form Editor.
	 */
	// <editor-fold defaultstate="collapsed"
	// <editor-fold defaultstate="collapsed"
	// <editor-fold defaultstate="collapsed"
	// <editor-fold defaultstate="collapsed"
	// <editor-fold defaultstate="collapsed"
	// <editor-fold defaultstate="collapsed"
	// <editor-fold defaultstate="collapsed"
	// <editor-fold defaultstate="collapsed"
	// <editor-fold defaultstate="collapsed"
	// <editor-fold defaultstate="collapsed"
	// <editor-fold defaultstate="collapsed"
	// <editor-fold defaultstate="collapsed"
	// <editor-fold defaultstate="collapsed"
	// <editor-fold defaultstate="collapsed"
	// <editor-fold defaultstate="collapsed"
	// <editor-fold defaultstate="collapsed"
	// <editor-fold defaultstate="collapsed"
	// <editor-fold defaultstate="collapsed"
	// desc="Generated Code">//GEN-BEGIN:initComponents
	private void initComponents() {

		pokeStatChangesButtonGroup = new javax.swing.ButtonGroup();
		pokeTypesButtonGroup = new javax.swing.ButtonGroup();
		pokeMovesetsButtonGroup = new javax.swing.ButtonGroup();
		trainerPokesButtonGroup = new javax.swing.ButtonGroup();
		wildPokesButtonGroup = new javax.swing.ButtonGroup();
		wildPokesARuleButtonGroup = new javax.swing.ButtonGroup();
		starterPokemonButtonGroup = new javax.swing.ButtonGroup();
		romOpenChooser = new javax.swing.JFileChooser();
		romSaveChooser = new JFileChooser() {

			private static final long serialVersionUID = 3244234325234511L;

			public void approveSelection() {
				File fh = getSelectedFile();
				// Fix or add extension
				List<String> extensions = new ArrayList<String>(Arrays.asList(
						"sgb", "gbc", "gba", "nds"));
				extensions.remove(RandomizerGUI.this.romHandler
						.getDefaultExtension());
				fh = FileFunctions.fixFilename(fh,
						RandomizerGUI.this.romHandler.getDefaultExtension(),
						extensions);
				if (fh.exists() && getDialogType() == SAVE_DIALOG) {
					int result = JOptionPane.showConfirmDialog(this,
							"The file exists, overwrite?", "Existing file",
							JOptionPane.YES_NO_CANCEL_OPTION);
					switch (result) {
					case JOptionPane.YES_OPTION:
						super.approveSelection();
						return;
					case JOptionPane.CANCEL_OPTION:
						cancelSelection();
						return;
					default:
						return;
					}
				}
				super.approveSelection();
			}
		};
		qsOpenChooser = new javax.swing.JFileChooser();
		qsSaveChooser = new javax.swing.JFileChooser();
		staticPokemonButtonGroup = new javax.swing.ButtonGroup();
		tmMovesButtonGroup = new javax.swing.ButtonGroup();
		tmHmCompatibilityButtonGroup = new javax.swing.ButtonGroup();
		pokeAbilitiesButtonGroup = new javax.swing.ButtonGroup();
		mtMovesButtonGroup = new javax.swing.ButtonGroup();
		mtCompatibilityButtonGroup = new javax.swing.ButtonGroup();
		ingameTradesButtonGroup = new javax.swing.ButtonGroup();
		fieldItemsButtonGroup = new javax.swing.ButtonGroup();
		updateSettingsMenu = new javax.swing.JPopupMenu();
		toggleAutoUpdatesMenuItem = new javax.swing.JMenuItem();
		manualUpdateMenuItem = new javax.swing.JMenuItem();
		generalOptionsPanel = new javax.swing.JPanel();
		goUpdateTypesCheckBox = new javax.swing.JCheckBox();
		goUpdateMovesCheckBox = new javax.swing.JCheckBox();
		goRemoveTradeEvosCheckBox = new javax.swing.JCheckBox();
		goLowerCaseNamesCheckBox = new javax.swing.JCheckBox();
		goNationalDexCheckBox = new javax.swing.JCheckBox();
		goUpdateMovesLegacyCheckBox = new javax.swing.JCheckBox();
		goCondenseEvosCheckBox = new javax.swing.JCheckBox();
		romInfoPanel = new javax.swing.JPanel();
		riRomNameLabel = new javax.swing.JLabel();
		riRomCodeLabel = new javax.swing.JLabel();
		riRomSupportLabel = new javax.swing.JLabel();
		optionsScrollPane = new javax.swing.JScrollPane();
		optionsScrollPane.getVerticalScrollBar().setUnitIncrement(16);
		optionsContainerPanel = new javax.swing.JPanel();
		baseStatsPanel = new javax.swing.JPanel();
		pbsChangesUnchangedRB = new javax.swing.JRadioButton();
		pbsChangesShuffleRB = new javax.swing.JRadioButton();
		pbsChangesRandomEvosRB = new javax.swing.JRadioButton();
		pbsChangesRandomTotalRB = new javax.swing.JRadioButton();
		pbsStandardEXPCurvesCB = new javax.swing.JCheckBox();
		pokemonTypesPanel = new javax.swing.JPanel();
		ptUnchangedRB = new javax.swing.JRadioButton();
		ptRandomFollowEvosRB = new javax.swing.JRadioButton();
		ptRandomTotalRB = new javax.swing.JRadioButton();
		pokemonMovesetsPanel = new javax.swing.JPanel();
		pmsUnchangedRB = new javax.swing.JRadioButton();
		pmsRandomTypeRB = new javax.swing.JRadioButton();
		pmsRandomTotalRB = new javax.swing.JRadioButton();
		pmsMetronomeOnlyRB = new javax.swing.JRadioButton();
		pms4MovesCB = new javax.swing.JCheckBox();
		trainersPokemonPanel = new javax.swing.JPanel();
		tpUnchangedRB = new javax.swing.JRadioButton();
		tpRandomRB = new javax.swing.JRadioButton();
		tpTypeThemedRB = new javax.swing.JRadioButton();
		tpPowerLevelsCB = new javax.swing.JCheckBox();
		tpTypeWeightingCB = new javax.swing.JCheckBox();
		tpRivalCarriesStarterCB = new javax.swing.JCheckBox();
		tpNoLegendariesCB = new javax.swing.JCheckBox();
		tnRandomizeCB = new javax.swing.JCheckBox();
		tcnRandomizeCB = new javax.swing.JCheckBox();
		tpNoEarlyShedinjaCB = new javax.swing.JCheckBox();
		wildPokemonPanel = new javax.swing.JPanel();
		wpUnchangedRB = new javax.swing.JRadioButton();
		wpRandomRB = new javax.swing.JRadioButton();
		wpArea11RB = new javax.swing.JRadioButton();
		wpGlobalRB = new javax.swing.JRadioButton();
		wildPokemonARulePanel = new javax.swing.JPanel();
		wpARNoneRB = new javax.swing.JRadioButton();
		wpARCatchEmAllRB = new javax.swing.JRadioButton();
		wpARTypeThemedRB = new javax.swing.JRadioButton();
		wpARSimilarStrengthRB = new javax.swing.JRadioButton();
		wpUseTimeCB = new javax.swing.JCheckBox();
		wpNoLegendariesCB = new javax.swing.JCheckBox();
		wpCatchRateCB = new javax.swing.JCheckBox();
		wpHeldItemsCB = new javax.swing.JCheckBox();
		wpHeldItemsBanBadCB = new javax.swing.JCheckBox();
		starterPokemonPanel = new javax.swing.JPanel();
		spUnchangedRB = new javax.swing.JRadioButton();
		spCustomRB = new javax.swing.JRadioButton();
		spCustomPoke1Chooser = new javax.swing.JComboBox();
		spCustomPoke2Chooser = new javax.swing.JComboBox();
		spCustomPoke3Chooser = new javax.swing.JComboBox();
		spRandomRB = new javax.swing.JRadioButton();
		spRandom2EvosRB = new javax.swing.JRadioButton();
		spHeldItemsCB = new javax.swing.JCheckBox();
		spHeldItemsBanBadCB = new javax.swing.JCheckBox();
		staticPokemonPanel = new javax.swing.JPanel();
		stpUnchangedRB = new javax.swing.JRadioButton();
		stpRandomL4LRB = new javax.swing.JRadioButton();
		stpRandomTotalRB = new javax.swing.JRadioButton();
		tmhmsPanel = new javax.swing.JPanel();
		tmMovesPanel = new javax.swing.JPanel();
		tmmUnchangedRB = new javax.swing.JRadioButton();
		tmmRandomRB = new javax.swing.JRadioButton();
		tmLearningSanityCB = new javax.swing.JCheckBox();
		tmKeepFieldMovesCB = new javax.swing.JCheckBox();
		tmHmCompatPanel = new javax.swing.JPanel();
		thcUnchangedRB = new javax.swing.JRadioButton();
		thcRandomTypeRB = new javax.swing.JRadioButton();
		thcRandomTotalRB = new javax.swing.JRadioButton();
		thcFullRB = new javax.swing.JRadioButton();
		abilitiesPanel = new javax.swing.JPanel();
		paUnchangedRB = new javax.swing.JRadioButton();
		paRandomizeRB = new javax.swing.JRadioButton();
		paWonderGuardCB = new javax.swing.JCheckBox();
		moveTutorsPanel = new javax.swing.JPanel();
		mtMovesPanel = new javax.swing.JPanel();
		mtmUnchangedRB = new javax.swing.JRadioButton();
		mtmRandomRB = new javax.swing.JRadioButton();
		mtLearningSanityCB = new javax.swing.JCheckBox();
		mtKeepFieldMovesCB = new javax.swing.JCheckBox();
		mtCompatPanel = new javax.swing.JPanel();
		mtcUnchangedRB = new javax.swing.JRadioButton();
		mtcRandomTypeRB = new javax.swing.JRadioButton();
		mtcRandomTotalRB = new javax.swing.JRadioButton();
		mtcFullRB = new javax.swing.JRadioButton();
		mtNoExistLabel = new javax.swing.JLabel();
		inGameTradesPanel = new javax.swing.JPanel();
		igtUnchangedRB = new javax.swing.JRadioButton();
		igtGivenOnlyRB = new javax.swing.JRadioButton();
		igtBothRB = new javax.swing.JRadioButton();
		igtRandomNicknameCB = new javax.swing.JCheckBox();
		igtRandomOTCB = new javax.swing.JCheckBox();
		igtRandomIVsCB = new javax.swing.JCheckBox();
		igtRandomItemCB = new javax.swing.JCheckBox();
		fieldItemsPanel = new javax.swing.JPanel();
		fiUnchangedRB = new javax.swing.JRadioButton();
		fiShuffleRB = new javax.swing.JRadioButton();
		fiRandomRB = new javax.swing.JRadioButton();
		fiBanBadCB = new javax.swing.JCheckBox();
		openROMButton = new javax.swing.JButton();
		saveROMButton = new javax.swing.JButton();
		usePresetsButton = new javax.swing.JButton();
		aboutButton = new javax.swing.JButton();
		otherOptionsPanel = new javax.swing.JPanel();
		codeTweaksCB = new javax.swing.JCheckBox();
		raceModeCB = new javax.swing.JCheckBox();
		randomizeHollowsCB = new javax.swing.JCheckBox();
		brokenMovesCB = new javax.swing.JCheckBox();
		codeTweaksBtn = new javax.swing.JButton();
		pokeLimitCB = new javax.swing.JCheckBox();
		pokeLimitBtn = new javax.swing.JButton();
		loadQSButton = new javax.swing.JButton();
		saveQSButton = new javax.swing.JButton();
		updateSettingsButton = new javax.swing.JButton();

		romOpenChooser.setFileFilter(new ROMFilter());

		romSaveChooser.setDialogType(javax.swing.JFileChooser.SAVE_DIALOG);
		romSaveChooser.setFileFilter(new ROMFilter());

		qsOpenChooser.setFileFilter(new QSFileFilter());

		qsSaveChooser.setDialogType(javax.swing.JFileChooser.SAVE_DIALOG);
		qsSaveChooser.setFileFilter(new QSFileFilter());

		java.util.ResourceBundle bundle = java.util.ResourceBundle
				.getBundle("com/dabomstew/pkrandom/gui/Bundle"); // NOI18N
		toggleAutoUpdatesMenuItem.setText(bundle
				.getString("RandomizerGUI.toggleAutoUpdatesMenuItem.text")); // NOI18N
		toggleAutoUpdatesMenuItem
				.addActionListener(new java.awt.event.ActionListener() {
					public void actionPerformed(java.awt.event.ActionEvent evt) {
						toggleAutoUpdatesMenuItemActionPerformed(evt);
					}
				});
		updateSettingsMenu.add(toggleAutoUpdatesMenuItem);

		manualUpdateMenuItem.setText(bundle
				.getString("RandomizerGUI.manualUpdateMenuItem.text")); // NOI18N
		manualUpdateMenuItem
				.addActionListener(new java.awt.event.ActionListener() {
					public void actionPerformed(java.awt.event.ActionEvent evt) {
						manualUpdateMenuItemActionPerformed(evt);
					}
				});
		updateSettingsMenu.add(manualUpdateMenuItem);

		setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
		setTitle(bundle.getString("RandomizerGUI.title")); // NOI18N

		generalOptionsPanel
				.setBorder(javax.swing.BorderFactory.createTitledBorder(
						null,
						bundle.getString("RandomizerGUI.generalOptionsPanel.border.title"),
						javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
						javax.swing.border.TitledBorder.DEFAULT_POSITION,
						new java.awt.Font("Tahoma", 1, 11))); // NOI18N

		goUpdateTypesCheckBox.setText(bundle
				.getString("RandomizerGUI.goUpdateTypesCheckBox.text")); // NOI18N
		goUpdateTypesCheckBox.setToolTipText(bundle
				.getString("RandomizerGUI.goUpdateTypesCheckBox.toolTipText")); // NOI18N

		goUpdateMovesCheckBox.setText(bundle
				.getString("RandomizerGUI.goUpdateMovesCheckBox.text")); // NOI18N
		goUpdateMovesCheckBox.setToolTipText(bundle
				.getString("RandomizerGUI.goUpdateMovesCheckBox.toolTipText")); // NOI18N
		goUpdateMovesCheckBox
				.addActionListener(new java.awt.event.ActionListener() {
					public void actionPerformed(java.awt.event.ActionEvent evt) {
						goUpdateMovesCheckBoxActionPerformed(evt);
					}
				});

		goRemoveTradeEvosCheckBox.setText(bundle
				.getString("RandomizerGUI.goRemoveTradeEvosCheckBox.text")); // NOI18N
		goRemoveTradeEvosCheckBox
				.setToolTipText(bundle
						.getString("RandomizerGUI.goRemoveTradeEvosCheckBox.toolTipText")); // NOI18N

		goLowerCaseNamesCheckBox.setText(bundle
				.getString("RandomizerGUI.goLowerCaseNamesCheckBox.text")); // NOI18N
		goLowerCaseNamesCheckBox
				.setToolTipText(bundle
						.getString("RandomizerGUI.goLowerCaseNamesCheckBox.toolTipText")); // NOI18N

		goNationalDexCheckBox.setText(bundle
				.getString("RandomizerGUI.goNationalDexCheckBox.text")); // NOI18N
		goNationalDexCheckBox.setToolTipText(bundle
				.getString("RandomizerGUI.goNationalDexCheckBox.toolTipText")); // NOI18N

		goUpdateMovesLegacyCheckBox.setText(bundle
				.getString("RandomizerGUI.goUpdateMovesLegacyCheckBox.text")); // NOI18N
		goUpdateMovesLegacyCheckBox
				.setToolTipText(bundle
						.getString("RandomizerGUI.goUpdateMovesLegacyCheckBox.toolTipText")); // NOI18N

		goCondenseEvosCheckBox.setText(bundle
				.getString("RandomizerGUI.goCondenseEvosCheckBox.text")); // NOI18N
		goCondenseEvosCheckBox.setToolTipText(bundle
				.getString("RandomizerGUI.goCondenseEvosCheckBox.toolTipText")); // NOI18N
		goCondenseEvosCheckBox
				.addActionListener(new java.awt.event.ActionListener() {
					public void actionPerformed(java.awt.event.ActionEvent evt) {
						goCondenseEvosCheckBoxActionPerformed(evt);
					}
				});

		javax.swing.GroupLayout generalOptionsPanelLayout = new javax.swing.GroupLayout(
				generalOptionsPanel);
		generalOptionsPanel.setLayout(generalOptionsPanelLayout);
		generalOptionsPanelLayout
				.setHorizontalGroup(generalOptionsPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								generalOptionsPanelLayout
										.createSequentialGroup()
										.addContainerGap()
										.addGroup(
												generalOptionsPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.LEADING)
														.addGroup(
																generalOptionsPanelLayout
																		.createSequentialGroup()
																		.addGroup(
																				generalOptionsPanelLayout
																						.createParallelGroup(
																								javax.swing.GroupLayout.Alignment.LEADING)
																						.addComponent(
																								goUpdateTypesCheckBox)
																						.addComponent(
																								goNationalDexCheckBox)
																						.addGroup(
																								generalOptionsPanelLayout
																										.createSequentialGroup()
																										.addComponent(
																												goUpdateMovesCheckBox)
																										.addPreferredGap(
																												javax.swing.LayoutStyle.ComponentPlacement.RELATED)
																										.addComponent(
																												goUpdateMovesLegacyCheckBox)))
																		.addContainerGap(
																				javax.swing.GroupLayout.DEFAULT_SIZE,
																				Short.MAX_VALUE))
														.addGroup(
																generalOptionsPanelLayout
																		.createSequentialGroup()
																		.addGroup(
																				generalOptionsPanelLayout
																						.createParallelGroup(
																								javax.swing.GroupLayout.Alignment.LEADING)
																						.addComponent(
																								goRemoveTradeEvosCheckBox)
																						.addComponent(
																								goCondenseEvosCheckBox))
																		.addGap(0,
																				0,
																				Short.MAX_VALUE))))
						.addGroup(
								javax.swing.GroupLayout.Alignment.TRAILING,
								generalOptionsPanelLayout
										.createSequentialGroup()
										.addContainerGap(
												javax.swing.GroupLayout.DEFAULT_SIZE,
												Short.MAX_VALUE)
										.addComponent(goLowerCaseNamesCheckBox)
										.addContainerGap()));
		generalOptionsPanelLayout
				.setVerticalGroup(generalOptionsPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								generalOptionsPanelLayout
										.createSequentialGroup()
										.addComponent(goUpdateTypesCheckBox)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addGroup(
												generalOptionsPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.BASELINE)
														.addComponent(
																goUpdateMovesCheckBox)
														.addComponent(
																goUpdateMovesLegacyCheckBox))
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addComponent(goRemoveTradeEvosCheckBox)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addComponent(goCondenseEvosCheckBox)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addComponent(goLowerCaseNamesCheckBox)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addComponent(goNationalDexCheckBox)
										.addContainerGap(
												javax.swing.GroupLayout.DEFAULT_SIZE,
												Short.MAX_VALUE)));

		romInfoPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(
				null,
				bundle.getString("RandomizerGUI.romInfoPanel.border.title"),
				javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
				javax.swing.border.TitledBorder.DEFAULT_POSITION,
				new java.awt.Font("Tahoma", 1, 11))); // NOI18N

		riRomNameLabel.setText(bundle
				.getString("RandomizerGUI.riRomNameLabel.text")); // NOI18N

		riRomCodeLabel.setText(bundle
				.getString("RandomizerGUI.riRomCodeLabel.text")); // NOI18N

		riRomSupportLabel.setText(bundle
				.getString("RandomizerGUI.riRomSupportLabel.text")); // NOI18N

		javax.swing.GroupLayout romInfoPanelLayout = new javax.swing.GroupLayout(
				romInfoPanel);
		romInfoPanel.setLayout(romInfoPanelLayout);
		romInfoPanelLayout
				.setHorizontalGroup(romInfoPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								romInfoPanelLayout
										.createSequentialGroup()
										.addContainerGap()
										.addGroup(
												romInfoPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.LEADING)
														.addComponent(
																riRomNameLabel)
														.addComponent(
																riRomCodeLabel)
														.addComponent(
																riRomSupportLabel))
										.addContainerGap(
												javax.swing.GroupLayout.DEFAULT_SIZE,
												Short.MAX_VALUE)));
		romInfoPanelLayout
				.setVerticalGroup(romInfoPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								romInfoPanelLayout
										.createSequentialGroup()
										.addGap(5, 5, 5)
										.addComponent(riRomNameLabel)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.RELATED)
										.addComponent(riRomCodeLabel)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.RELATED)
										.addComponent(riRomSupportLabel)
										.addContainerGap(
												javax.swing.GroupLayout.DEFAULT_SIZE,
												Short.MAX_VALUE)));

		baseStatsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(
				null,
				bundle.getString("RandomizerGUI.baseStatsPanel.border.title"),
				javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
				javax.swing.border.TitledBorder.DEFAULT_POSITION,
				new java.awt.Font("Tahoma", 1, 11))); // NOI18N

		pokeStatChangesButtonGroup.add(pbsChangesUnchangedRB);
		pbsChangesUnchangedRB.setSelected(true);
		pbsChangesUnchangedRB.setText(bundle
				.getString("RandomizerGUI.pbsChangesUnchangedRB.text")); // NOI18N
		pbsChangesUnchangedRB.setToolTipText(bundle
				.getString("RandomizerGUI.pbsChangesUnchangedRB.toolTipText")); // NOI18N

		pokeStatChangesButtonGroup.add(pbsChangesShuffleRB);
		pbsChangesShuffleRB.setText(bundle
				.getString("RandomizerGUI.pbsChangesShuffleRB.text")); // NOI18N
		pbsChangesShuffleRB.setToolTipText(bundle
				.getString("RandomizerGUI.pbsChangesShuffleRB.toolTipText")); // NOI18N

		pokeStatChangesButtonGroup.add(pbsChangesRandomEvosRB);
		pbsChangesRandomEvosRB.setText(bundle
				.getString("RandomizerGUI.pbsChangesRandomEvosRB.text")); // NOI18N
		pbsChangesRandomEvosRB.setToolTipText(bundle
				.getString("RandomizerGUI.pbsChangesRandomEvosRB.toolTipText")); // NOI18N

		pokeStatChangesButtonGroup.add(pbsChangesRandomTotalRB);
		pbsChangesRandomTotalRB.setText(bundle
				.getString("RandomizerGUI.pbsChangesRandomTotalRB.text")); // NOI18N
		pbsChangesRandomTotalRB
				.setToolTipText(bundle
						.getString("RandomizerGUI.pbsChangesRandomTotalRB.toolTipText")); // NOI18N

		pbsStandardEXPCurvesCB.setText(bundle
				.getString("RandomizerGUI.pbsStandardEXPCurvesCB.text")); // NOI18N
		pbsStandardEXPCurvesCB.setToolTipText(bundle
				.getString("RandomizerGUI.pbsStandardEXPCurvesCB.toolTipText")); // NOI18N

		javax.swing.GroupLayout baseStatsPanelLayout = new javax.swing.GroupLayout(
				baseStatsPanel);
		baseStatsPanel.setLayout(baseStatsPanelLayout);
		baseStatsPanelLayout
				.setHorizontalGroup(baseStatsPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								baseStatsPanelLayout
										.createSequentialGroup()
										.addContainerGap()
										.addGroup(
												baseStatsPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.LEADING)
														.addGroup(
																baseStatsPanelLayout
																		.createSequentialGroup()
																		.addGroup(
																				baseStatsPanelLayout
																						.createParallelGroup(
																								javax.swing.GroupLayout.Alignment.LEADING)
																						.addComponent(
																								pbsChangesUnchangedRB)
																						.addComponent(
																								pbsChangesRandomEvosRB)
																						.addComponent(
																								pbsChangesRandomTotalRB))
																		.addContainerGap(
																				javax.swing.GroupLayout.DEFAULT_SIZE,
																				Short.MAX_VALUE))
														.addGroup(
																baseStatsPanelLayout
																		.createSequentialGroup()
																		.addComponent(
																				pbsChangesShuffleRB)
																		.addPreferredGap(
																				javax.swing.LayoutStyle.ComponentPlacement.RELATED,
																				125,
																				Short.MAX_VALUE)
																		.addComponent(
																				pbsStandardEXPCurvesCB)
																		.addGap(38,
																				38,
																				38)))));
		baseStatsPanelLayout
				.setVerticalGroup(baseStatsPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								baseStatsPanelLayout
										.createSequentialGroup()
										.addContainerGap()
										.addComponent(pbsChangesUnchangedRB)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addGroup(
												baseStatsPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.BASELINE)
														.addComponent(
																pbsChangesShuffleRB)
														.addComponent(
																pbsStandardEXPCurvesCB))
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addComponent(pbsChangesRandomEvosRB)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addComponent(pbsChangesRandomTotalRB)
										.addContainerGap(
												javax.swing.GroupLayout.DEFAULT_SIZE,
												Short.MAX_VALUE)));

		pokemonTypesPanel
				.setBorder(javax.swing.BorderFactory.createTitledBorder(
						null,
						bundle.getString("RandomizerGUI.pokemonTypesPanel.border.title"),
						javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
						javax.swing.border.TitledBorder.DEFAULT_POSITION,
						new java.awt.Font("Tahoma", 1, 11))); // NOI18N

		pokeTypesButtonGroup.add(ptUnchangedRB);
		ptUnchangedRB.setSelected(true);
		ptUnchangedRB.setText(bundle
				.getString("RandomizerGUI.ptUnchangedRB.text")); // NOI18N
		ptUnchangedRB.setToolTipText(bundle
				.getString("RandomizerGUI.ptUnchangedRB.toolTipText")); // NOI18N

		pokeTypesButtonGroup.add(ptRandomFollowEvosRB);
		ptRandomFollowEvosRB.setText(bundle
				.getString("RandomizerGUI.ptRandomFollowEvosRB.text")); // NOI18N
		ptRandomFollowEvosRB.setToolTipText(bundle
				.getString("RandomizerGUI.ptRandomFollowEvosRB.toolTipText")); // NOI18N

		pokeTypesButtonGroup.add(ptRandomTotalRB);
		ptRandomTotalRB.setText(bundle
				.getString("RandomizerGUI.ptRandomTotalRB.text")); // NOI18N
		ptRandomTotalRB.setToolTipText(bundle
				.getString("RandomizerGUI.ptRandomTotalRB.toolTipText")); // NOI18N

		javax.swing.GroupLayout pokemonTypesPanelLayout = new javax.swing.GroupLayout(
				pokemonTypesPanel);
		pokemonTypesPanel.setLayout(pokemonTypesPanelLayout);
		pokemonTypesPanelLayout
				.setHorizontalGroup(pokemonTypesPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								pokemonTypesPanelLayout
										.createSequentialGroup()
										.addContainerGap()
										.addGroup(
												pokemonTypesPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.LEADING)
														.addComponent(
																ptUnchangedRB)
														.addComponent(
																ptRandomFollowEvosRB)
														.addComponent(
																ptRandomTotalRB))
										.addContainerGap(
												javax.swing.GroupLayout.DEFAULT_SIZE,
												Short.MAX_VALUE)));
		pokemonTypesPanelLayout
				.setVerticalGroup(pokemonTypesPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								pokemonTypesPanelLayout
										.createSequentialGroup()
										.addContainerGap()
										.addComponent(ptUnchangedRB)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addComponent(ptRandomFollowEvosRB)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addComponent(ptRandomTotalRB)
										.addContainerGap(
												javax.swing.GroupLayout.DEFAULT_SIZE,
												Short.MAX_VALUE)));

		pokemonMovesetsPanel
				.setBorder(javax.swing.BorderFactory.createTitledBorder(
						null,
						bundle.getString("RandomizerGUI.pokemonMovesetsPanel.border.title"),
						javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
						javax.swing.border.TitledBorder.DEFAULT_POSITION,
						new java.awt.Font("Tahoma", 1, 11))); // NOI18N

		pokeMovesetsButtonGroup.add(pmsUnchangedRB);
		pmsUnchangedRB.setSelected(true);
		pmsUnchangedRB.setText(bundle
				.getString("RandomizerGUI.pmsUnchangedRB.text")); // NOI18N
		pmsUnchangedRB.setToolTipText(bundle
				.getString("RandomizerGUI.pmsUnchangedRB.toolTipText")); // NOI18N
		pmsUnchangedRB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				pmsUnchangedRBActionPerformed(evt);
			}
		});

		pokeMovesetsButtonGroup.add(pmsRandomTypeRB);
		pmsRandomTypeRB.setText(bundle
				.getString("RandomizerGUI.pmsRandomTypeRB.text")); // NOI18N
		pmsRandomTypeRB.setToolTipText(bundle
				.getString("RandomizerGUI.pmsRandomTypeRB.toolTipText")); // NOI18N
		pmsRandomTypeRB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				pmsRandomTypeRBActionPerformed(evt);
			}
		});

		pokeMovesetsButtonGroup.add(pmsRandomTotalRB);
		pmsRandomTotalRB.setText(bundle
				.getString("RandomizerGUI.pmsRandomTotalRB.text")); // NOI18N
		pmsRandomTotalRB.setToolTipText(bundle
				.getString("RandomizerGUI.pmsRandomTotalRB.toolTipText")); // NOI18N
		pmsRandomTotalRB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				pmsRandomTotalRBActionPerformed(evt);
			}
		});

		pokeMovesetsButtonGroup.add(pmsMetronomeOnlyRB);
		pmsMetronomeOnlyRB.setText(bundle
				.getString("RandomizerGUI.pmsMetronomeOnlyRB.text")); // NOI18N
		pmsMetronomeOnlyRB.setToolTipText(bundle
				.getString("RandomizerGUI.pmsMetronomeOnlyRB.toolTipText")); // NOI18N
		pmsMetronomeOnlyRB
				.addActionListener(new java.awt.event.ActionListener() {
					public void actionPerformed(java.awt.event.ActionEvent evt) {
						pmsMetronomeOnlyRBActionPerformed(evt);
					}
				});

		pms4MovesCB.setText(bundle.getString("RandomizerGUI.pms4MovesCB.text")); // NOI18N
		pms4MovesCB.setToolTipText(bundle
				.getString("RandomizerGUI.pms4MovesCB.toolTipText")); // NOI18N

		javax.swing.GroupLayout pokemonMovesetsPanelLayout = new javax.swing.GroupLayout(
				pokemonMovesetsPanel);
		pokemonMovesetsPanel.setLayout(pokemonMovesetsPanelLayout);
		pokemonMovesetsPanelLayout
				.setHorizontalGroup(pokemonMovesetsPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								pokemonMovesetsPanelLayout
										.createSequentialGroup()
										.addContainerGap()
										.addGroup(
												pokemonMovesetsPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.LEADING)
														.addComponent(
																pmsUnchangedRB)
														.addGroup(
																pokemonMovesetsPanelLayout
																		.createSequentialGroup()
																		.addComponent(
																				pmsRandomTypeRB)
																		.addGap(198,
																				198,
																				198)
																		.addComponent(
																				pms4MovesCB))
														.addComponent(
																pmsRandomTotalRB)
														.addComponent(
																pmsMetronomeOnlyRB))
										.addContainerGap(134, Short.MAX_VALUE)));
		pokemonMovesetsPanelLayout
				.setVerticalGroup(pokemonMovesetsPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								pokemonMovesetsPanelLayout
										.createSequentialGroup()
										.addContainerGap(
												javax.swing.GroupLayout.DEFAULT_SIZE,
												Short.MAX_VALUE)
										.addComponent(pmsUnchangedRB)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addGroup(
												pokemonMovesetsPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.BASELINE)
														.addComponent(
																pmsRandomTypeRB)
														.addComponent(
																pms4MovesCB))
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addComponent(pmsRandomTotalRB)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addComponent(pmsMetronomeOnlyRB)));

		trainersPokemonPanel
				.setBorder(javax.swing.BorderFactory.createTitledBorder(
						null,
						bundle.getString("RandomizerGUI.trainersPokemonPanel.border.title"),
						javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
						javax.swing.border.TitledBorder.DEFAULT_POSITION,
						new java.awt.Font("Tahoma", 1, 11))); // NOI18N

		trainerPokesButtonGroup.add(tpUnchangedRB);
		tpUnchangedRB.setSelected(true);
		tpUnchangedRB.setText(bundle
				.getString("RandomizerGUI.tpUnchangedRB.text")); // NOI18N
		tpUnchangedRB.setToolTipText(bundle
				.getString("RandomizerGUI.tpUnchangedRB.toolTipText")); // NOI18N
		tpUnchangedRB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				tpUnchangedRBActionPerformed(evt);
			}
		});

		trainerPokesButtonGroup.add(tpRandomRB);
		tpRandomRB.setText(bundle.getString("RandomizerGUI.tpRandomRB.text")); // NOI18N
		tpRandomRB.setToolTipText(bundle
				.getString("RandomizerGUI.tpRandomRB.toolTipText")); // NOI18N
		tpRandomRB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				tpRandomRBActionPerformed(evt);
			}
		});

		trainerPokesButtonGroup.add(tpTypeThemedRB);
		tpTypeThemedRB.setText(bundle
				.getString("RandomizerGUI.tpTypeThemedRB.text")); // NOI18N
		tpTypeThemedRB.setToolTipText(bundle
				.getString("RandomizerGUI.tpTypeThemedRB.toolTipText")); // NOI18N
		tpTypeThemedRB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				tpTypeThemedRBActionPerformed(evt);
			}
		});

		tpPowerLevelsCB.setText(bundle
				.getString("RandomizerGUI.tpPowerLevelsCB.text")); // NOI18N
		tpPowerLevelsCB.setToolTipText(bundle
				.getString("RandomizerGUI.tpPowerLevelsCB.toolTipText")); // NOI18N
		tpPowerLevelsCB.setEnabled(false);

		tpTypeWeightingCB.setText(bundle
				.getString("RandomizerGUI.tpTypeWeightingCB.text")); // NOI18N
		tpTypeWeightingCB.setToolTipText(bundle
				.getString("RandomizerGUI.tpTypeWeightingCB.toolTipText")); // NOI18N
		tpTypeWeightingCB.setEnabled(false);

		tpRivalCarriesStarterCB.setText(bundle
				.getString("RandomizerGUI.tpRivalCarriesStarterCB.text")); // NOI18N
		tpRivalCarriesStarterCB
				.setToolTipText(bundle
						.getString("RandomizerGUI.tpRivalCarriesStarterCB.toolTipText")); // NOI18N
		tpRivalCarriesStarterCB.setEnabled(false);

		tpNoLegendariesCB.setText(bundle
				.getString("RandomizerGUI.tpNoLegendariesCB.text")); // NOI18N
		tpNoLegendariesCB.setEnabled(false);

		tnRandomizeCB.setText(bundle
				.getString("RandomizerGUI.tnRandomizeCB.text")); // NOI18N
		tnRandomizeCB.setToolTipText(bundle
				.getString("RandomizerGUI.tnRandomizeCB.toolTipText")); // NOI18N

		tcnRandomizeCB.setText(bundle
				.getString("RandomizerGUI.tcnRandomizeCB.text")); // NOI18N
		tcnRandomizeCB.setToolTipText(bundle
				.getString("RandomizerGUI.tcnRandomizeCB.toolTipText")); // NOI18N

		tpNoEarlyShedinjaCB.setText(bundle
				.getString("RandomizerGUI.tpNoEarlyShedinjaCB.text")); // NOI18N
		tpNoEarlyShedinjaCB.setToolTipText(bundle
				.getString("RandomizerGUI.tpNoEarlyShedinjaCB.toolTipText")); // NOI18N

		javax.swing.GroupLayout trainersPokemonPanelLayout = new javax.swing.GroupLayout(
				trainersPokemonPanel);
		trainersPokemonPanel.setLayout(trainersPokemonPanelLayout);
		trainersPokemonPanelLayout
				.setHorizontalGroup(trainersPokemonPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								trainersPokemonPanelLayout
										.createSequentialGroup()
										.addContainerGap()
										.addGroup(
												trainersPokemonPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.LEADING)
														.addComponent(
																tpTypeThemedRB)
														.addGroup(
																trainersPokemonPanelLayout
																		.createSequentialGroup()
																		.addGroup(
																				trainersPokemonPanelLayout
																						.createParallelGroup(
																								javax.swing.GroupLayout.Alignment.LEADING)
																						.addComponent(
																								tpUnchangedRB)
																						.addComponent(
																								tpRandomRB))
																		.addGap(47,
																				47,
																				47)
																		.addGroup(
																				trainersPokemonPanelLayout
																						.createParallelGroup(
																								javax.swing.GroupLayout.Alignment.LEADING)
																						.addComponent(
																								tpNoEarlyShedinjaCB)
																						.addGroup(
																								trainersPokemonPanelLayout
																										.createSequentialGroup()
																										.addGroup(
																												trainersPokemonPanelLayout
																														.createParallelGroup(
																																javax.swing.GroupLayout.Alignment.LEADING,
																																false)
																														.addComponent(
																																tpTypeWeightingCB,
																																javax.swing.GroupLayout.DEFAULT_SIZE,
																																javax.swing.GroupLayout.DEFAULT_SIZE,
																																Short.MAX_VALUE)
																														.addComponent(
																																tpRivalCarriesStarterCB,
																																javax.swing.GroupLayout.DEFAULT_SIZE,
																																javax.swing.GroupLayout.DEFAULT_SIZE,
																																Short.MAX_VALUE)
																														.addComponent(
																																tpPowerLevelsCB,
																																javax.swing.GroupLayout.DEFAULT_SIZE,
																																javax.swing.GroupLayout.DEFAULT_SIZE,
																																Short.MAX_VALUE)
																														.addComponent(
																																tpNoLegendariesCB,
																																javax.swing.GroupLayout.DEFAULT_SIZE,
																																javax.swing.GroupLayout.DEFAULT_SIZE,
																																Short.MAX_VALUE))
																										.addGap(18,
																												18,
																												18)
																										.addGroup(
																												trainersPokemonPanelLayout
																														.createParallelGroup(
																																javax.swing.GroupLayout.Alignment.LEADING)
																														.addComponent(
																																tnRandomizeCB)
																														.addComponent(
																																tcnRandomizeCB))))))
										.addContainerGap(160, Short.MAX_VALUE)));
		trainersPokemonPanelLayout
				.setVerticalGroup(trainersPokemonPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								trainersPokemonPanelLayout
										.createSequentialGroup()
										.addGroup(
												trainersPokemonPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.BASELINE)
														.addComponent(
																tpUnchangedRB)
														.addComponent(
																tpRivalCarriesStarterCB)
														.addComponent(
																tnRandomizeCB))
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addGroup(
												trainersPokemonPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.BASELINE)
														.addComponent(
																tpRandomRB)
														.addComponent(
																tpPowerLevelsCB)
														.addComponent(
																tcnRandomizeCB))
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addGroup(
												trainersPokemonPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.BASELINE)
														.addComponent(
																tpTypeThemedRB)
														.addComponent(
																tpTypeWeightingCB))
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addComponent(tpNoLegendariesCB)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addComponent(tpNoEarlyShedinjaCB)
										.addContainerGap(
												javax.swing.GroupLayout.DEFAULT_SIZE,
												Short.MAX_VALUE)));

		wildPokemonPanel
				.setBorder(javax.swing.BorderFactory.createTitledBorder(
						null,
						bundle.getString("RandomizerGUI.wildPokemonPanel.border.title"),
						javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
						javax.swing.border.TitledBorder.DEFAULT_POSITION,
						new java.awt.Font("Tahoma", 1, 11))); // NOI18N

		wildPokesButtonGroup.add(wpUnchangedRB);
		wpUnchangedRB.setSelected(true);
		wpUnchangedRB.setText(bundle
				.getString("RandomizerGUI.wpUnchangedRB.text")); // NOI18N
		wpUnchangedRB.setToolTipText(bundle
				.getString("RandomizerGUI.wpUnchangedRB.toolTipText")); // NOI18N
		wpUnchangedRB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				wpUnchangedRBActionPerformed(evt);
			}
		});

		wildPokesButtonGroup.add(wpRandomRB);
		wpRandomRB.setText(bundle.getString("RandomizerGUI.wpRandomRB.text")); // NOI18N
		wpRandomRB.setToolTipText(bundle
				.getString("RandomizerGUI.wpRandomRB.toolTipText")); // NOI18N
		wpRandomRB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				wpRandomRBActionPerformed(evt);
			}
		});

		wildPokesButtonGroup.add(wpArea11RB);
		wpArea11RB.setText(bundle.getString("RandomizerGUI.wpArea11RB.text")); // NOI18N
		wpArea11RB.setToolTipText(bundle
				.getString("RandomizerGUI.wpArea11RB.toolTipText")); // NOI18N
		wpArea11RB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				wpArea11RBActionPerformed(evt);
			}
		});

		wildPokesButtonGroup.add(wpGlobalRB);
		wpGlobalRB.setText(bundle.getString("RandomizerGUI.wpGlobalRB.text")); // NOI18N
		wpGlobalRB.setToolTipText(bundle
				.getString("RandomizerGUI.wpGlobalRB.toolTipText")); // NOI18N
		wpGlobalRB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				wpGlobalRBActionPerformed(evt);
			}
		});

		wildPokemonARulePanel
				.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle
						.getString("RandomizerGUI.wildPokemonARulePanel.border.title"))); // NOI18N

		wildPokesARuleButtonGroup.add(wpARNoneRB);
		wpARNoneRB.setSelected(true);
		wpARNoneRB.setText(bundle.getString("RandomizerGUI.wpARNoneRB.text")); // NOI18N
		wpARNoneRB.setToolTipText(bundle
				.getString("RandomizerGUI.wpARNoneRB.toolTipText")); // NOI18N
		wpARNoneRB.setEnabled(false);
		wpARNoneRB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				wpARNoneRBActionPerformed(evt);
			}
		});

		wildPokesARuleButtonGroup.add(wpARCatchEmAllRB);
		wpARCatchEmAllRB.setText(bundle
				.getString("RandomizerGUI.wpARCatchEmAllRB.text")); // NOI18N
		wpARCatchEmAllRB.setToolTipText(bundle
				.getString("RandomizerGUI.wpARCatchEmAllRB.toolTipText")); // NOI18N
		wpARCatchEmAllRB.setEnabled(false);
		wpARCatchEmAllRB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				wpARCatchEmAllRBActionPerformed(evt);
			}
		});

		wildPokesARuleButtonGroup.add(wpARTypeThemedRB);
		wpARTypeThemedRB.setText(bundle
				.getString("RandomizerGUI.wpARTypeThemedRB.text")); // NOI18N
		wpARTypeThemedRB.setToolTipText(bundle
				.getString("RandomizerGUI.wpARTypeThemedRB.toolTipText")); // NOI18N
		wpARTypeThemedRB.setEnabled(false);
		wpARTypeThemedRB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				wpARTypeThemedRBActionPerformed(evt);
			}
		});

		wildPokesARuleButtonGroup.add(wpARSimilarStrengthRB);
		wpARSimilarStrengthRB.setText(bundle
				.getString("RandomizerGUI.wpARSimilarStrengthRB.text")); // NOI18N
		wpARSimilarStrengthRB.setToolTipText(bundle
				.getString("RandomizerGUI.wpARSimilarStrengthRB.toolTipText")); // NOI18N
		wpARSimilarStrengthRB.setEnabled(false);
		wpARSimilarStrengthRB
				.addActionListener(new java.awt.event.ActionListener() {
					public void actionPerformed(java.awt.event.ActionEvent evt) {
						wpARSimilarStrengthRBActionPerformed(evt);
					}
				});

		javax.swing.GroupLayout wildPokemonARulePanelLayout = new javax.swing.GroupLayout(
				wildPokemonARulePanel);
		wildPokemonARulePanel.setLayout(wildPokemonARulePanelLayout);
		wildPokemonARulePanelLayout
				.setHorizontalGroup(wildPokemonARulePanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								wildPokemonARulePanelLayout
										.createSequentialGroup()
										.addContainerGap()
										.addGroup(
												wildPokemonARulePanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.LEADING)
														.addGroup(
																wildPokemonARulePanelLayout
																		.createSequentialGroup()
																		.addComponent(
																				wpARTypeThemedRB)
																		.addGap(0,
																				0,
																				Short.MAX_VALUE))
														.addGroup(
																wildPokemonARulePanelLayout
																		.createSequentialGroup()
																		.addGroup(
																				wildPokemonARulePanelLayout
																						.createParallelGroup(
																								javax.swing.GroupLayout.Alignment.LEADING)
																						.addComponent(
																								wpARSimilarStrengthRB)
																						.addComponent(
																								wpARNoneRB)
																						.addComponent(
																								wpARCatchEmAllRB))
																		.addContainerGap(
																				58,
																				Short.MAX_VALUE)))));
		wildPokemonARulePanelLayout
				.setVerticalGroup(wildPokemonARulePanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								wildPokemonARulePanelLayout
										.createSequentialGroup()
										.addComponent(wpARNoneRB)
										.addGap(3, 3, 3)
										.addComponent(wpARSimilarStrengthRB)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addComponent(wpARCatchEmAllRB)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.RELATED,
												3, Short.MAX_VALUE)
										.addComponent(
												wpARTypeThemedRB,
												javax.swing.GroupLayout.PREFERRED_SIZE,
												30,
												javax.swing.GroupLayout.PREFERRED_SIZE)
										.addContainerGap()));

		wpUseTimeCB.setText(bundle.getString("RandomizerGUI.wpUseTimeCB.text")); // NOI18N
		wpUseTimeCB.setToolTipText(bundle
				.getString("RandomizerGUI.wpUseTimeCB.toolTipText")); // NOI18N

		wpNoLegendariesCB.setText(bundle
				.getString("RandomizerGUI.wpNoLegendariesCB.text")); // NOI18N

		wpCatchRateCB.setText(bundle
				.getString("RandomizerGUI.wpCatchRateCB.text")); // NOI18N
		wpCatchRateCB.setToolTipText(bundle
				.getString("RandomizerGUI.wpCatchRateCB.toolTipText")); // NOI18N

		wpHeldItemsCB.setText(bundle
				.getString("RandomizerGUI.wpHeldItemsCB.text")); // NOI18N
		wpHeldItemsCB.setToolTipText(bundle
				.getString("RandomizerGUI.wpHeldItemsCB.toolTipText")); // NOI18N
		wpHeldItemsCB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				wpHeldItemsCBActionPerformed(evt);
			}
		});

		wpHeldItemsBanBadCB.setText(bundle
				.getString("RandomizerGUI.wpHeldItemsBanBadCB.text")); // NOI18N
		wpHeldItemsBanBadCB.setToolTipText(bundle
				.getString("RandomizerGUI.wpHeldItemsBanBadCB.toolTipText")); // NOI18N

		javax.swing.GroupLayout wildPokemonPanelLayout = new javax.swing.GroupLayout(
				wildPokemonPanel);
		wildPokemonPanel.setLayout(wildPokemonPanelLayout);
		wildPokemonPanelLayout
				.setHorizontalGroup(wildPokemonPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								wildPokemonPanelLayout
										.createSequentialGroup()
										.addContainerGap()
										.addGroup(
												wildPokemonPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.LEADING)
														.addComponent(
																wpUnchangedRB)
														.addComponent(
																wpRandomRB)
														.addComponent(
																wpArea11RB)
														.addComponent(
																wpGlobalRB))
										.addGap(18, 18, 18)
										.addComponent(
												wildPokemonARulePanel,
												javax.swing.GroupLayout.PREFERRED_SIZE,
												javax.swing.GroupLayout.DEFAULT_SIZE,
												javax.swing.GroupLayout.PREFERRED_SIZE)
										.addGap(18, 18, 18)
										.addGroup(
												wildPokemonPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.LEADING)
														.addComponent(
																wpUseTimeCB)
														.addComponent(
																wpNoLegendariesCB)
														.addComponent(
																wpCatchRateCB)
														.addComponent(
																wpHeldItemsCB)
														.addComponent(
																wpHeldItemsBanBadCB))
										.addContainerGap(
												javax.swing.GroupLayout.DEFAULT_SIZE,
												Short.MAX_VALUE)));
		wildPokemonPanelLayout
				.setVerticalGroup(wildPokemonPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.TRAILING)
						.addGroup(
								wildPokemonPanelLayout
										.createSequentialGroup()
										.addContainerGap()
										.addGroup(
												wildPokemonPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.LEADING)
														.addGroup(
																wildPokemonPanelLayout
																		.createSequentialGroup()
																		.addComponent(
																				wpUnchangedRB)
																		.addPreferredGap(
																				javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
																		.addComponent(
																				wpRandomRB)
																		.addPreferredGap(
																				javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
																		.addComponent(
																				wpArea11RB)
																		.addPreferredGap(
																				javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
																		.addComponent(
																				wpGlobalRB))
														.addGroup(
																wildPokemonPanelLayout
																		.createSequentialGroup()
																		.addComponent(
																				wpUseTimeCB)
																		.addPreferredGap(
																				javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
																		.addComponent(
																				wpNoLegendariesCB)
																		.addPreferredGap(
																				javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
																		.addComponent(
																				wpCatchRateCB)
																		.addPreferredGap(
																				javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
																		.addComponent(
																				wpHeldItemsCB)
																		.addPreferredGap(
																				javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
																		.addComponent(
																				wpHeldItemsBanBadCB))
														.addComponent(
																wildPokemonARulePanel,
																javax.swing.GroupLayout.PREFERRED_SIZE,
																javax.swing.GroupLayout.DEFAULT_SIZE,
																javax.swing.GroupLayout.PREFERRED_SIZE))));

		starterPokemonPanel
				.setBorder(javax.swing.BorderFactory.createTitledBorder(
						null,
						bundle.getString("RandomizerGUI.starterPokemonPanel.border.title"),
						javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
						javax.swing.border.TitledBorder.DEFAULT_POSITION,
						new java.awt.Font("Tahoma", 1, 11))); // NOI18N

		starterPokemonButtonGroup.add(spUnchangedRB);
		spUnchangedRB.setSelected(true);
		spUnchangedRB.setText(bundle
				.getString("RandomizerGUI.spUnchangedRB.text")); // NOI18N
		spUnchangedRB.setToolTipText(bundle
				.getString("RandomizerGUI.spUnchangedRB.toolTipText")); // NOI18N
		spUnchangedRB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				spUnchangedRBActionPerformed(evt);
			}
		});

		starterPokemonButtonGroup.add(spCustomRB);
		spCustomRB.setText(bundle.getString("RandomizerGUI.spCustomRB.text")); // NOI18N
		spCustomRB.setToolTipText(bundle
				.getString("RandomizerGUI.spCustomRB.toolTipText")); // NOI18N
		spCustomRB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				spCustomRBActionPerformed(evt);
			}
		});

		spCustomPoke1Chooser.setModel(new javax.swing.DefaultComboBoxModel(
				new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
		spCustomPoke1Chooser.setEnabled(false);

		spCustomPoke2Chooser.setModel(new javax.swing.DefaultComboBoxModel(
				new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
		spCustomPoke2Chooser.setEnabled(false);

		spCustomPoke3Chooser.setModel(new javax.swing.DefaultComboBoxModel(
				new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
		spCustomPoke3Chooser.setEnabled(false);

		starterPokemonButtonGroup.add(spRandomRB);
		spRandomRB.setText(bundle.getString("RandomizerGUI.spRandomRB.text")); // NOI18N
		spRandomRB.setToolTipText(bundle
				.getString("RandomizerGUI.spRandomRB.toolTipText")); // NOI18N
		spRandomRB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				spRandomRBActionPerformed(evt);
			}
		});

		starterPokemonButtonGroup.add(spRandom2EvosRB);
		spRandom2EvosRB.setText(bundle
				.getString("RandomizerGUI.spRandom2EvosRB.text")); // NOI18N
		spRandom2EvosRB.setToolTipText(bundle
				.getString("RandomizerGUI.spRandom2EvosRB.toolTipText")); // NOI18N
		spRandom2EvosRB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				spRandom2EvosRBActionPerformed(evt);
			}
		});

		spHeldItemsCB.setText(bundle
				.getString("RandomizerGUI.spHeldItemsCB.text")); // NOI18N
		spHeldItemsCB.setToolTipText(bundle
				.getString("RandomizerGUI.spHeldItemsCB.toolTipText")); // NOI18N
		spHeldItemsCB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				spHeldItemsCBActionPerformed(evt);
			}
		});

		spHeldItemsBanBadCB.setText(bundle
				.getString("RandomizerGUI.spHeldItemsBanBadCB.text")); // NOI18N
		spHeldItemsBanBadCB.setToolTipText(bundle
				.getString("RandomizerGUI.spHeldItemsBanBadCB.toolTipText")); // NOI18N

		javax.swing.GroupLayout starterPokemonPanelLayout = new javax.swing.GroupLayout(
				starterPokemonPanel);
		starterPokemonPanel.setLayout(starterPokemonPanelLayout);
		starterPokemonPanelLayout
				.setHorizontalGroup(starterPokemonPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								starterPokemonPanelLayout
										.createSequentialGroup()
										.addContainerGap()
										.addGroup(
												starterPokemonPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.LEADING)
														.addComponent(
																spUnchangedRB)
														.addComponent(
																spRandom2EvosRB)
														.addGroup(
																starterPokemonPanelLayout
																		.createSequentialGroup()
																		.addGroup(
																				starterPokemonPanelLayout
																						.createParallelGroup(
																								javax.swing.GroupLayout.Alignment.LEADING)
																						.addGroup(
																								starterPokemonPanelLayout
																										.createSequentialGroup()
																										.addComponent(
																												spCustomRB)
																										.addGap(18,
																												18,
																												18)
																										.addComponent(
																												spCustomPoke1Chooser,
																												javax.swing.GroupLayout.PREFERRED_SIZE,
																												90,
																												javax.swing.GroupLayout.PREFERRED_SIZE)
																										.addPreferredGap(
																												javax.swing.LayoutStyle.ComponentPlacement.RELATED)
																										.addComponent(
																												spCustomPoke2Chooser,
																												javax.swing.GroupLayout.PREFERRED_SIZE,
																												90,
																												javax.swing.GroupLayout.PREFERRED_SIZE)
																										.addPreferredGap(
																												javax.swing.LayoutStyle.ComponentPlacement.RELATED)
																										.addComponent(
																												spCustomPoke3Chooser,
																												javax.swing.GroupLayout.PREFERRED_SIZE,
																												90,
																												javax.swing.GroupLayout.PREFERRED_SIZE))
																						.addComponent(
																								spRandomRB))
																		.addGap(18,
																				18,
																				18)
																		.addGroup(
																				starterPokemonPanelLayout
																						.createParallelGroup(
																								javax.swing.GroupLayout.Alignment.LEADING)
																						.addComponent(
																								spHeldItemsBanBadCB)
																						.addComponent(
																								spHeldItemsCB))))
										.addContainerGap(162, Short.MAX_VALUE)));
		starterPokemonPanelLayout
				.setVerticalGroup(starterPokemonPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								starterPokemonPanelLayout
										.createSequentialGroup()
										.addContainerGap()
										.addComponent(spUnchangedRB)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addGroup(
												starterPokemonPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.BASELINE)
														.addComponent(
																spCustomRB)
														.addComponent(
																spCustomPoke1Chooser,
																javax.swing.GroupLayout.PREFERRED_SIZE,
																javax.swing.GroupLayout.DEFAULT_SIZE,
																javax.swing.GroupLayout.PREFERRED_SIZE)
														.addComponent(
																spCustomPoke2Chooser,
																javax.swing.GroupLayout.PREFERRED_SIZE,
																javax.swing.GroupLayout.DEFAULT_SIZE,
																javax.swing.GroupLayout.PREFERRED_SIZE)
														.addComponent(
																spCustomPoke3Chooser,
																javax.swing.GroupLayout.PREFERRED_SIZE,
																javax.swing.GroupLayout.DEFAULT_SIZE,
																javax.swing.GroupLayout.PREFERRED_SIZE)
														.addComponent(
																spHeldItemsCB))
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addGroup(
												starterPokemonPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.BASELINE)
														.addComponent(
																spRandomRB)
														.addComponent(
																spHeldItemsBanBadCB))
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addComponent(spRandom2EvosRB)
										.addContainerGap(11, Short.MAX_VALUE)));

		staticPokemonPanel
				.setBorder(javax.swing.BorderFactory.createTitledBorder(
						null,
						bundle.getString("RandomizerGUI.staticPokemonPanel.border.title"),
						javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
						javax.swing.border.TitledBorder.DEFAULT_POSITION,
						new java.awt.Font("Tahoma", 1, 11))); // NOI18N

		staticPokemonButtonGroup.add(stpUnchangedRB);
		stpUnchangedRB.setSelected(true);
		stpUnchangedRB.setText(bundle
				.getString("RandomizerGUI.stpUnchangedRB.text")); // NOI18N
		stpUnchangedRB.setToolTipText(bundle
				.getString("RandomizerGUI.stpUnchangedRB.toolTipText")); // NOI18N

		staticPokemonButtonGroup.add(stpRandomL4LRB);
		stpRandomL4LRB.setText(bundle
				.getString("RandomizerGUI.stpRandomL4LRB.text")); // NOI18N
		stpRandomL4LRB.setToolTipText(bundle
				.getString("RandomizerGUI.stpRandomL4LRB.toolTipText")); // NOI18N

		staticPokemonButtonGroup.add(stpRandomTotalRB);
		stpRandomTotalRB.setText(bundle
				.getString("RandomizerGUI.stpRandomTotalRB.text")); // NOI18N
		stpRandomTotalRB.setToolTipText(bundle
				.getString("RandomizerGUI.stpRandomTotalRB.toolTipText")); // NOI18N

		javax.swing.GroupLayout staticPokemonPanelLayout = new javax.swing.GroupLayout(
				staticPokemonPanel);
		staticPokemonPanel.setLayout(staticPokemonPanelLayout);
		staticPokemonPanelLayout
				.setHorizontalGroup(staticPokemonPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								staticPokemonPanelLayout
										.createSequentialGroup()
										.addContainerGap()
										.addGroup(
												staticPokemonPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.LEADING)
														.addComponent(
																stpUnchangedRB)
														.addComponent(
																stpRandomL4LRB)
														.addComponent(
																stpRandomTotalRB))
										.addContainerGap(401, Short.MAX_VALUE)));
		staticPokemonPanelLayout
				.setVerticalGroup(staticPokemonPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								staticPokemonPanelLayout
										.createSequentialGroup()
										.addComponent(stpUnchangedRB)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addComponent(stpRandomL4LRB)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addComponent(stpRandomTotalRB)));

		tmhmsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(null,
				bundle.getString("RandomizerGUI.tmhmsPanel.border.title"),
				javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
				javax.swing.border.TitledBorder.DEFAULT_POSITION,
				new java.awt.Font("Tahoma", 1, 11))); // NOI18N

		tmMovesPanel.setBorder(javax.swing.BorderFactory
				.createTitledBorder(bundle
						.getString("RandomizerGUI.tmMovesPanel.border.title"))); // NOI18N

		tmMovesButtonGroup.add(tmmUnchangedRB);
		tmmUnchangedRB.setSelected(true);
		tmmUnchangedRB.setText(bundle
				.getString("RandomizerGUI.tmmUnchangedRB.text")); // NOI18N
		tmmUnchangedRB.setToolTipText(bundle
				.getString("RandomizerGUI.tmmUnchangedRB.toolTipText")); // NOI18N
		tmmUnchangedRB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				tmmUnchangedRBActionPerformed(evt);
			}
		});

		tmMovesButtonGroup.add(tmmRandomRB);
		tmmRandomRB.setText(bundle.getString("RandomizerGUI.tmmRandomRB.text")); // NOI18N
		tmmRandomRB.setToolTipText(bundle
				.getString("RandomizerGUI.tmmRandomRB.toolTipText")); // NOI18N
		tmmRandomRB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				tmmRandomRBActionPerformed(evt);
			}
		});

		tmLearningSanityCB.setText(bundle
				.getString("RandomizerGUI.tmLearningSanityCB.text")); // NOI18N
		tmLearningSanityCB.setToolTipText(bundle
				.getString("RandomizerGUI.tmLearningSanityCB.toolTipText")); // NOI18N

		tmKeepFieldMovesCB.setText(bundle
				.getString("RandomizerGUI.tmKeepFieldMovesCB.text")); // NOI18N
		tmKeepFieldMovesCB.setToolTipText(bundle
				.getString("RandomizerGUI.tmKeepFieldMovesCB.toolTipText")); // NOI18N

		javax.swing.GroupLayout tmMovesPanelLayout = new javax.swing.GroupLayout(
				tmMovesPanel);
		tmMovesPanel.setLayout(tmMovesPanelLayout);
		tmMovesPanelLayout
				.setHorizontalGroup(tmMovesPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								tmMovesPanelLayout
										.createSequentialGroup()
										.addContainerGap()
										.addGroup(
												tmMovesPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.LEADING)
														.addComponent(
																tmmUnchangedRB)
														.addComponent(
																tmmRandomRB))
										.addGap(67, 67, 67)
										.addGroup(
												tmMovesPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.LEADING)
														.addComponent(
																tmKeepFieldMovesCB)
														.addComponent(
																tmLearningSanityCB))
										.addContainerGap(105, Short.MAX_VALUE)));
		tmMovesPanelLayout
				.setVerticalGroup(tmMovesPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								tmMovesPanelLayout
										.createSequentialGroup()
										.addContainerGap()
										.addGroup(
												tmMovesPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.BASELINE)
														.addComponent(
																tmmUnchangedRB)
														.addComponent(
																tmLearningSanityCB))
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addGroup(
												tmMovesPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.BASELINE)
														.addComponent(
																tmmRandomRB)
														.addComponent(
																tmKeepFieldMovesCB))
										.addContainerGap(
												javax.swing.GroupLayout.DEFAULT_SIZE,
												Short.MAX_VALUE)));

		tmHmCompatPanel
				.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle
						.getString("RandomizerGUI.tmHmCompatPanel.border.title"))); // NOI18N

		tmHmCompatibilityButtonGroup.add(thcUnchangedRB);
		thcUnchangedRB.setSelected(true);
		thcUnchangedRB.setText(bundle
				.getString("RandomizerGUI.thcUnchangedRB.text")); // NOI18N
		thcUnchangedRB.setToolTipText(bundle
				.getString("RandomizerGUI.thcUnchangedRB.toolTipText")); // NOI18N
		thcUnchangedRB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				thcUnchangedRBActionPerformed(evt);
			}
		});

		tmHmCompatibilityButtonGroup.add(thcRandomTypeRB);
		thcRandomTypeRB.setText(bundle
				.getString("RandomizerGUI.thcRandomTypeRB.text")); // NOI18N
		thcRandomTypeRB.setToolTipText(bundle
				.getString("RandomizerGUI.thcRandomTypeRB.toolTipText")); // NOI18N
		thcRandomTypeRB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				thcRandomTypeRBActionPerformed(evt);
			}
		});

		tmHmCompatibilityButtonGroup.add(thcRandomTotalRB);
		thcRandomTotalRB.setText(bundle
				.getString("RandomizerGUI.thcRandomTotalRB.text")); // NOI18N
		thcRandomTotalRB.setToolTipText(bundle
				.getString("RandomizerGUI.thcRandomTotalRB.toolTipText")); // NOI18N
		thcRandomTotalRB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				thcRandomTotalRBActionPerformed(evt);
			}
		});

		tmHmCompatibilityButtonGroup.add(thcFullRB);
		thcFullRB.setText(bundle.getString("RandomizerGUI.thcFullRB.text")); // NOI18N
		thcFullRB.setToolTipText(bundle
				.getString("RandomizerGUI.thcFullRB.toolTipText")); // NOI18N
		thcFullRB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				thcFullRBActionPerformed(evt);
			}
		});

		javax.swing.GroupLayout tmHmCompatPanelLayout = new javax.swing.GroupLayout(
				tmHmCompatPanel);
		tmHmCompatPanel.setLayout(tmHmCompatPanelLayout);
		tmHmCompatPanelLayout
				.setHorizontalGroup(tmHmCompatPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								tmHmCompatPanelLayout
										.createSequentialGroup()
										.addContainerGap()
										.addGroup(
												tmHmCompatPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.LEADING)
														.addComponent(
																thcUnchangedRB)
														.addComponent(
																thcRandomTypeRB)
														.addComponent(
																thcRandomTotalRB)
														.addComponent(thcFullRB))
										.addContainerGap(79, Short.MAX_VALUE)));
		tmHmCompatPanelLayout
				.setVerticalGroup(tmHmCompatPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								tmHmCompatPanelLayout
										.createSequentialGroup()
										.addContainerGap()
										.addComponent(thcUnchangedRB)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addComponent(thcRandomTypeRB)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addComponent(thcRandomTotalRB)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addComponent(thcFullRB)
										.addContainerGap(
												javax.swing.GroupLayout.DEFAULT_SIZE,
												Short.MAX_VALUE)));

		javax.swing.GroupLayout tmhmsPanelLayout = new javax.swing.GroupLayout(
				tmhmsPanel);
		tmhmsPanel.setLayout(tmhmsPanelLayout);
		tmhmsPanelLayout
				.setHorizontalGroup(tmhmsPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								tmhmsPanelLayout
										.createSequentialGroup()
										.addContainerGap()
										.addComponent(
												tmMovesPanel,
												javax.swing.GroupLayout.PREFERRED_SIZE,
												javax.swing.GroupLayout.DEFAULT_SIZE,
												javax.swing.GroupLayout.PREFERRED_SIZE)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.RELATED,
												javax.swing.GroupLayout.DEFAULT_SIZE,
												Short.MAX_VALUE)
										.addComponent(
												tmHmCompatPanel,
												javax.swing.GroupLayout.PREFERRED_SIZE,
												javax.swing.GroupLayout.DEFAULT_SIZE,
												javax.swing.GroupLayout.PREFERRED_SIZE)
										.addContainerGap()));
		tmhmsPanelLayout
				.setVerticalGroup(tmhmsPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								tmhmsPanelLayout
										.createSequentialGroup()
										.addContainerGap(16, Short.MAX_VALUE)
										.addGroup(
												tmhmsPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.LEADING,
																false)
														.addComponent(
																tmHmCompatPanel,
																javax.swing.GroupLayout.DEFAULT_SIZE,
																javax.swing.GroupLayout.DEFAULT_SIZE,
																Short.MAX_VALUE)
														.addComponent(
																tmMovesPanel,
																javax.swing.GroupLayout.DEFAULT_SIZE,
																javax.swing.GroupLayout.DEFAULT_SIZE,
																Short.MAX_VALUE))));

		abilitiesPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(
				null,
				bundle.getString("RandomizerGUI.abilitiesPanel.border.title"),
				javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
				javax.swing.border.TitledBorder.DEFAULT_POSITION,
				new java.awt.Font("Tahoma", 1, 11))); // NOI18N

		pokeAbilitiesButtonGroup.add(paUnchangedRB);
		paUnchangedRB.setSelected(true);
		paUnchangedRB.setText(bundle
				.getString("RandomizerGUI.paUnchangedRB.text")); // NOI18N
		paUnchangedRB.setToolTipText(bundle
				.getString("RandomizerGUI.paUnchangedRB.toolTipText")); // NOI18N
		paUnchangedRB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				paUnchangedRBActionPerformed(evt);
			}
		});

		pokeAbilitiesButtonGroup.add(paRandomizeRB);
		paRandomizeRB.setText(bundle
				.getString("RandomizerGUI.paRandomizeRB.text")); // NOI18N
		paRandomizeRB.setToolTipText(bundle
				.getString("RandomizerGUI.paRandomizeRB.toolTipText")); // NOI18N
		paRandomizeRB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				paRandomizeRBActionPerformed(evt);
			}
		});

		paWonderGuardCB.setText(bundle
				.getString("RandomizerGUI.paWonderGuardCB.text")); // NOI18N
		paWonderGuardCB.setToolTipText(bundle
				.getString("RandomizerGUI.paWonderGuardCB.toolTipText")); // NOI18N

		javax.swing.GroupLayout abilitiesPanelLayout = new javax.swing.GroupLayout(
				abilitiesPanel);
		abilitiesPanel.setLayout(abilitiesPanelLayout);
		abilitiesPanelLayout
				.setHorizontalGroup(abilitiesPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								abilitiesPanelLayout
										.createSequentialGroup()
										.addContainerGap()
										.addGroup(
												abilitiesPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.LEADING)
														.addComponent(
																paUnchangedRB)
														.addComponent(
																paRandomizeRB)
														.addComponent(
																paWonderGuardCB))
										.addContainerGap(190, Short.MAX_VALUE)));
		abilitiesPanelLayout
				.setVerticalGroup(abilitiesPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								abilitiesPanelLayout
										.createSequentialGroup()
										.addContainerGap()
										.addComponent(paUnchangedRB)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addComponent(paRandomizeRB)
										.addGap(18, 18, 18)
										.addComponent(paWonderGuardCB)
										.addContainerGap(
												javax.swing.GroupLayout.DEFAULT_SIZE,
												Short.MAX_VALUE)));

		moveTutorsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(
				null,
				bundle.getString("RandomizerGUI.moveTutorsPanel.border.title"),
				javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
				javax.swing.border.TitledBorder.DEFAULT_POSITION,
				new java.awt.Font("Tahoma", 1, 11))); // NOI18N

		mtMovesPanel.setBorder(javax.swing.BorderFactory
				.createTitledBorder(bundle
						.getString("RandomizerGUI.mtMovesPanel.border.title"))); // NOI18N

		mtMovesButtonGroup.add(mtmUnchangedRB);
		mtmUnchangedRB.setSelected(true);
		mtmUnchangedRB.setText(bundle
				.getString("RandomizerGUI.mtmUnchangedRB.text")); // NOI18N
		mtmUnchangedRB.setToolTipText(bundle
				.getString("RandomizerGUI.mtmUnchangedRB.toolTipText")); // NOI18N
		mtmUnchangedRB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				mtmUnchangedRBActionPerformed(evt);
			}
		});

		mtMovesButtonGroup.add(mtmRandomRB);
		mtmRandomRB.setText(bundle.getString("RandomizerGUI.mtmRandomRB.text")); // NOI18N
		mtmRandomRB.setToolTipText(bundle
				.getString("RandomizerGUI.mtmRandomRB.toolTipText")); // NOI18N
		mtmRandomRB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				mtmRandomRBActionPerformed(evt);
			}
		});

		mtLearningSanityCB.setText(bundle
				.getString("RandomizerGUI.mtLearningSanityCB.text")); // NOI18N
		mtLearningSanityCB.setToolTipText(bundle
				.getString("RandomizerGUI.mtLearningSanityCB.toolTipText")); // NOI18N

		mtKeepFieldMovesCB.setText(bundle
				.getString("RandomizerGUI.mtKeepFieldMovesCB.text")); // NOI18N
		mtKeepFieldMovesCB.setToolTipText(bundle
				.getString("RandomizerGUI.mtKeepFieldMovesCB.toolTipText")); // NOI18N

		javax.swing.GroupLayout mtMovesPanelLayout = new javax.swing.GroupLayout(
				mtMovesPanel);
		mtMovesPanel.setLayout(mtMovesPanelLayout);
		mtMovesPanelLayout
				.setHorizontalGroup(mtMovesPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								mtMovesPanelLayout
										.createSequentialGroup()
										.addContainerGap()
										.addGroup(
												mtMovesPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.LEADING)
														.addComponent(
																mtmUnchangedRB)
														.addComponent(
																mtmRandomRB))
										.addGap(64, 64, 64)
										.addGroup(
												mtMovesPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.LEADING)
														.addComponent(
																mtKeepFieldMovesCB)
														.addComponent(
																mtLearningSanityCB))
										.addContainerGap(94, Short.MAX_VALUE)));
		mtMovesPanelLayout
				.setVerticalGroup(mtMovesPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								mtMovesPanelLayout
										.createSequentialGroup()
										.addContainerGap()
										.addGroup(
												mtMovesPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.BASELINE)
														.addComponent(
																mtmUnchangedRB)
														.addComponent(
																mtLearningSanityCB))
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addGroup(
												mtMovesPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.BASELINE)
														.addComponent(
																mtmRandomRB)
														.addComponent(
																mtKeepFieldMovesCB))
										.addContainerGap(
												javax.swing.GroupLayout.DEFAULT_SIZE,
												Short.MAX_VALUE)));

		mtCompatPanel
				.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle
						.getString("RandomizerGUI.mtCompatPanel.border.title"))); // NOI18N

		mtCompatibilityButtonGroup.add(mtcUnchangedRB);
		mtcUnchangedRB.setSelected(true);
		mtcUnchangedRB.setText(bundle
				.getString("RandomizerGUI.mtcUnchangedRB.text")); // NOI18N
		mtcUnchangedRB.setToolTipText(bundle
				.getString("RandomizerGUI.mtcUnchangedRB.toolTipText")); // NOI18N
		mtcUnchangedRB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				mtcUnchangedRBActionPerformed(evt);
			}
		});

		mtCompatibilityButtonGroup.add(mtcRandomTypeRB);
		mtcRandomTypeRB.setText(bundle
				.getString("RandomizerGUI.mtcRandomTypeRB.text")); // NOI18N
		mtcRandomTypeRB.setToolTipText(bundle
				.getString("RandomizerGUI.mtcRandomTypeRB.toolTipText")); // NOI18N
		mtcRandomTypeRB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				mtcRandomTypeRBActionPerformed(evt);
			}
		});

		mtCompatibilityButtonGroup.add(mtcRandomTotalRB);
		mtcRandomTotalRB.setText(bundle
				.getString("RandomizerGUI.mtcRandomTotalRB.text")); // NOI18N
		mtcRandomTotalRB.setToolTipText(bundle
				.getString("RandomizerGUI.mtcRandomTotalRB.toolTipText")); // NOI18N
		mtcRandomTotalRB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				mtcRandomTotalRBActionPerformed(evt);
			}
		});

		mtCompatibilityButtonGroup.add(mtcFullRB);
		mtcFullRB.setText(bundle.getString("RandomizerGUI.mtcFullRB.text")); // NOI18N
		mtcFullRB.setToolTipText(bundle
				.getString("RandomizerGUI.mtcFullRB.toolTipText")); // NOI18N
		mtcFullRB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				mtcFullRBActionPerformed(evt);
			}
		});

		javax.swing.GroupLayout mtCompatPanelLayout = new javax.swing.GroupLayout(
				mtCompatPanel);
		mtCompatPanel.setLayout(mtCompatPanelLayout);
		mtCompatPanelLayout
				.setHorizontalGroup(mtCompatPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								mtCompatPanelLayout
										.createSequentialGroup()
										.addContainerGap()
										.addGroup(
												mtCompatPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.LEADING)
														.addComponent(
																mtcUnchangedRB)
														.addComponent(
																mtcRandomTypeRB)
														.addComponent(
																mtcRandomTotalRB)
														.addComponent(mtcFullRB))
										.addContainerGap(79, Short.MAX_VALUE)));
		mtCompatPanelLayout
				.setVerticalGroup(mtCompatPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								mtCompatPanelLayout
										.createSequentialGroup()
										.addContainerGap()
										.addComponent(mtcUnchangedRB)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addComponent(mtcRandomTypeRB)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addComponent(mtcRandomTotalRB)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addComponent(mtcFullRB)
										.addContainerGap(
												javax.swing.GroupLayout.DEFAULT_SIZE,
												Short.MAX_VALUE)));

		mtNoExistLabel.setText(bundle
				.getString("RandomizerGUI.mtNoExistLabel.text")); // NOI18N

		javax.swing.GroupLayout moveTutorsPanelLayout = new javax.swing.GroupLayout(
				moveTutorsPanel);
		moveTutorsPanel.setLayout(moveTutorsPanelLayout);
		moveTutorsPanelLayout
				.setHorizontalGroup(moveTutorsPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								moveTutorsPanelLayout
										.createSequentialGroup()
										.addContainerGap()
										.addGroup(
												moveTutorsPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.LEADING)
														.addGroup(
																moveTutorsPanelLayout
																		.createSequentialGroup()
																		.addComponent(
																				mtMovesPanel,
																				javax.swing.GroupLayout.PREFERRED_SIZE,
																				javax.swing.GroupLayout.DEFAULT_SIZE,
																				javax.swing.GroupLayout.PREFERRED_SIZE)
																		.addGap(18,
																				18,
																				18)
																		.addComponent(
																				mtCompatPanel,
																				javax.swing.GroupLayout.PREFERRED_SIZE,
																				javax.swing.GroupLayout.DEFAULT_SIZE,
																				javax.swing.GroupLayout.PREFERRED_SIZE))
														.addComponent(
																mtNoExistLabel))
										.addContainerGap(18, Short.MAX_VALUE)));
		moveTutorsPanelLayout
				.setVerticalGroup(moveTutorsPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								moveTutorsPanelLayout
										.createSequentialGroup()
										.addComponent(mtNoExistLabel)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addGroup(
												moveTutorsPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.LEADING)
														.addComponent(
																mtCompatPanel,
																javax.swing.GroupLayout.DEFAULT_SIZE,
																javax.swing.GroupLayout.DEFAULT_SIZE,
																Short.MAX_VALUE)
														.addComponent(
																mtMovesPanel,
																javax.swing.GroupLayout.DEFAULT_SIZE,
																javax.swing.GroupLayout.DEFAULT_SIZE,
																Short.MAX_VALUE))));

		inGameTradesPanel
				.setBorder(javax.swing.BorderFactory.createTitledBorder(
						null,
						bundle.getString("RandomizerGUI.inGameTradesPanel.border.title"),
						javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
						javax.swing.border.TitledBorder.DEFAULT_POSITION,
						new java.awt.Font("Tahoma", 1, 11))); // NOI18N

		ingameTradesButtonGroup.add(igtUnchangedRB);
		igtUnchangedRB.setSelected(true);
		igtUnchangedRB.setText(bundle
				.getString("RandomizerGUI.igtUnchangedRB.text")); // NOI18N
		igtUnchangedRB.setToolTipText(bundle
				.getString("RandomizerGUI.igtUnchangedRB.toolTipText")); // NOI18N
		igtUnchangedRB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				igtUnchangedRBActionPerformed(evt);
			}
		});

		ingameTradesButtonGroup.add(igtGivenOnlyRB);
		igtGivenOnlyRB.setText(bundle
				.getString("RandomizerGUI.igtGivenOnlyRB.text")); // NOI18N
		igtGivenOnlyRB.setToolTipText(bundle
				.getString("RandomizerGUI.igtGivenOnlyRB.toolTipText")); // NOI18N
		igtGivenOnlyRB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				igtGivenOnlyRBActionPerformed(evt);
			}
		});

		ingameTradesButtonGroup.add(igtBothRB);
		igtBothRB.setText(bundle.getString("RandomizerGUI.igtBothRB.text")); // NOI18N
		igtBothRB.setToolTipText(bundle
				.getString("RandomizerGUI.igtBothRB.toolTipText")); // NOI18N
		igtBothRB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				igtBothRBActionPerformed(evt);
			}
		});

		igtRandomNicknameCB.setText(bundle
				.getString("RandomizerGUI.igtRandomNicknameCB.text")); // NOI18N
		igtRandomNicknameCB.setToolTipText(bundle
				.getString("RandomizerGUI.igtRandomNicknameCB.toolTipText")); // NOI18N

		igtRandomOTCB.setText(bundle
				.getString("RandomizerGUI.igtRandomOTCB.text")); // NOI18N
		igtRandomOTCB.setToolTipText(bundle
				.getString("RandomizerGUI.igtRandomOTCB.toolTipText")); // NOI18N

		igtRandomIVsCB.setText(bundle
				.getString("RandomizerGUI.igtRandomIVsCB.text")); // NOI18N
		igtRandomIVsCB.setToolTipText(bundle
				.getString("RandomizerGUI.igtRandomIVsCB.toolTipText")); // NOI18N

		igtRandomItemCB.setText(bundle
				.getString("RandomizerGUI.igtRandomItemCB.text")); // NOI18N
		igtRandomItemCB.setToolTipText(bundle
				.getString("RandomizerGUI.igtRandomItemCB.toolTipText")); // NOI18N

		javax.swing.GroupLayout inGameTradesPanelLayout = new javax.swing.GroupLayout(
				inGameTradesPanel);
		inGameTradesPanel.setLayout(inGameTradesPanelLayout);
		inGameTradesPanelLayout
				.setHorizontalGroup(inGameTradesPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								javax.swing.GroupLayout.Alignment.TRAILING,
								inGameTradesPanelLayout
										.createSequentialGroup()
										.addContainerGap()
										.addGroup(
												inGameTradesPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.LEADING)
														.addComponent(
																igtUnchangedRB)
														.addComponent(
																igtGivenOnlyRB)
														.addComponent(igtBothRB))
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.RELATED,
												javax.swing.GroupLayout.DEFAULT_SIZE,
												Short.MAX_VALUE)
										.addGroup(
												inGameTradesPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.LEADING)
														.addComponent(
																igtRandomItemCB)
														.addComponent(
																igtRandomNicknameCB)
														.addComponent(
																igtRandomOTCB)
														.addComponent(
																igtRandomIVsCB))
										.addGap(113, 113, 113)));
		inGameTradesPanelLayout
				.setVerticalGroup(inGameTradesPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								inGameTradesPanelLayout
										.createSequentialGroup()
										.addGroup(
												inGameTradesPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.BASELINE)
														.addComponent(
																igtUnchangedRB)
														.addComponent(
																igtRandomNicknameCB))
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addGroup(
												inGameTradesPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.BASELINE)
														.addComponent(
																igtGivenOnlyRB)
														.addComponent(
																igtRandomOTCB))
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addGroup(
												inGameTradesPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.BASELINE)
														.addComponent(igtBothRB)
														.addComponent(
																igtRandomIVsCB))
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addComponent(igtRandomItemCB)
										.addContainerGap(
												javax.swing.GroupLayout.DEFAULT_SIZE,
												Short.MAX_VALUE)));

		fieldItemsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(
				null,
				bundle.getString("RandomizerGUI.fieldItemsPanel.border.title"),
				javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
				javax.swing.border.TitledBorder.DEFAULT_POSITION,
				new java.awt.Font("Tahoma", 1, 11))); // NOI18N

		fieldItemsButtonGroup.add(fiUnchangedRB);
		fiUnchangedRB.setSelected(true);
		fiUnchangedRB.setText(bundle
				.getString("RandomizerGUI.fiUnchangedRB.text")); // NOI18N
		fiUnchangedRB.setToolTipText(bundle
				.getString("RandomizerGUI.fiUnchangedRB.toolTipText")); // NOI18N
		fiUnchangedRB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				fiUnchangedRBActionPerformed(evt);
			}
		});

		fieldItemsButtonGroup.add(fiShuffleRB);
		fiShuffleRB.setText(bundle.getString("RandomizerGUI.fiShuffleRB.text")); // NOI18N
		fiShuffleRB.setToolTipText(bundle
				.getString("RandomizerGUI.fiShuffleRB.toolTipText")); // NOI18N
		fiShuffleRB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				fiShuffleRBActionPerformed(evt);
			}
		});

		fieldItemsButtonGroup.add(fiRandomRB);
		fiRandomRB.setText(bundle.getString("RandomizerGUI.fiRandomRB.text")); // NOI18N
		fiRandomRB.setToolTipText(bundle
				.getString("RandomizerGUI.fiRandomRB.toolTipText")); // NOI18N
		fiRandomRB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				fiRandomRBActionPerformed(evt);
			}
		});

		fiBanBadCB.setText(bundle.getString("RandomizerGUI.fiBanBadCB.text")); // NOI18N
		fiBanBadCB.setToolTipText(bundle
				.getString("RandomizerGUI.fiBanBadCB.toolTipText")); // NOI18N

		javax.swing.GroupLayout fieldItemsPanelLayout = new javax.swing.GroupLayout(
				fieldItemsPanel);
		fieldItemsPanel.setLayout(fieldItemsPanelLayout);
		fieldItemsPanelLayout
				.setHorizontalGroup(fieldItemsPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								fieldItemsPanelLayout
										.createSequentialGroup()
										.addContainerGap()
										.addGroup(
												fieldItemsPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.LEADING)
														.addComponent(
																fiUnchangedRB)
														.addComponent(
																fiShuffleRB)
														.addGroup(
																fieldItemsPanelLayout
																		.createSequentialGroup()
																		.addComponent(
																				fiRandomRB)
																		.addGap(76,
																				76,
																				76)
																		.addComponent(
																				fiBanBadCB)))
										.addContainerGap(460, Short.MAX_VALUE)));
		fieldItemsPanelLayout
				.setVerticalGroup(fieldItemsPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								fieldItemsPanelLayout
										.createSequentialGroup()
										.addComponent(fiUnchangedRB)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addComponent(fiShuffleRB)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addGroup(
												fieldItemsPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.BASELINE)
														.addComponent(
																fiRandomRB)
														.addComponent(
																fiBanBadCB))));

		javax.swing.GroupLayout optionsContainerPanelLayout = new javax.swing.GroupLayout(
				optionsContainerPanel);
		optionsContainerPanel.setLayout(optionsContainerPanelLayout);
		optionsContainerPanelLayout
				.setHorizontalGroup(optionsContainerPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addComponent(pokemonTypesPanel,
								javax.swing.GroupLayout.DEFAULT_SIZE,
								javax.swing.GroupLayout.DEFAULT_SIZE,
								Short.MAX_VALUE)
						.addComponent(pokemonMovesetsPanel,
								javax.swing.GroupLayout.Alignment.TRAILING,
								javax.swing.GroupLayout.DEFAULT_SIZE,
								javax.swing.GroupLayout.DEFAULT_SIZE,
								Short.MAX_VALUE)
						.addComponent(trainersPokemonPanel,
								javax.swing.GroupLayout.DEFAULT_SIZE,
								javax.swing.GroupLayout.DEFAULT_SIZE,
								Short.MAX_VALUE)
						.addComponent(wildPokemonPanel,
								javax.swing.GroupLayout.DEFAULT_SIZE,
								javax.swing.GroupLayout.DEFAULT_SIZE,
								Short.MAX_VALUE)
						.addComponent(starterPokemonPanel,
								javax.swing.GroupLayout.DEFAULT_SIZE,
								javax.swing.GroupLayout.DEFAULT_SIZE,
								Short.MAX_VALUE)
						.addComponent(staticPokemonPanel,
								javax.swing.GroupLayout.DEFAULT_SIZE,
								javax.swing.GroupLayout.DEFAULT_SIZE,
								Short.MAX_VALUE)
						.addComponent(tmhmsPanel,
								javax.swing.GroupLayout.DEFAULT_SIZE,
								javax.swing.GroupLayout.DEFAULT_SIZE,
								Short.MAX_VALUE)
						.addGroup(
								optionsContainerPanelLayout
										.createSequentialGroup()
										.addComponent(
												baseStatsPanel,
												javax.swing.GroupLayout.PREFERRED_SIZE,
												javax.swing.GroupLayout.DEFAULT_SIZE,
												javax.swing.GroupLayout.PREFERRED_SIZE)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addComponent(
												abilitiesPanel,
												javax.swing.GroupLayout.DEFAULT_SIZE,
												javax.swing.GroupLayout.DEFAULT_SIZE,
												Short.MAX_VALUE))
						.addComponent(moveTutorsPanel,
								javax.swing.GroupLayout.DEFAULT_SIZE,
								javax.swing.GroupLayout.DEFAULT_SIZE,
								Short.MAX_VALUE)
						.addComponent(inGameTradesPanel,
								javax.swing.GroupLayout.DEFAULT_SIZE,
								javax.swing.GroupLayout.DEFAULT_SIZE,
								Short.MAX_VALUE)
						.addComponent(fieldItemsPanel,
								javax.swing.GroupLayout.DEFAULT_SIZE,
								javax.swing.GroupLayout.DEFAULT_SIZE,
								Short.MAX_VALUE));
		optionsContainerPanelLayout
				.setVerticalGroup(optionsContainerPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								optionsContainerPanelLayout
										.createSequentialGroup()
										.addGroup(
												optionsContainerPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.LEADING,
																false)
														.addComponent(
																baseStatsPanel,
																javax.swing.GroupLayout.DEFAULT_SIZE,
																javax.swing.GroupLayout.DEFAULT_SIZE,
																Short.MAX_VALUE)
														.addComponent(
																abilitiesPanel,
																javax.swing.GroupLayout.DEFAULT_SIZE,
																javax.swing.GroupLayout.DEFAULT_SIZE,
																Short.MAX_VALUE))
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addComponent(
												starterPokemonPanel,
												javax.swing.GroupLayout.PREFERRED_SIZE,
												javax.swing.GroupLayout.DEFAULT_SIZE,
												javax.swing.GroupLayout.PREFERRED_SIZE)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.RELATED)
										.addComponent(
												pokemonTypesPanel,
												javax.swing.GroupLayout.PREFERRED_SIZE,
												javax.swing.GroupLayout.DEFAULT_SIZE,
												javax.swing.GroupLayout.PREFERRED_SIZE)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addComponent(
												pokemonMovesetsPanel,
												javax.swing.GroupLayout.PREFERRED_SIZE,
												javax.swing.GroupLayout.DEFAULT_SIZE,
												javax.swing.GroupLayout.PREFERRED_SIZE)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addComponent(
												trainersPokemonPanel,
												javax.swing.GroupLayout.PREFERRED_SIZE,
												javax.swing.GroupLayout.DEFAULT_SIZE,
												javax.swing.GroupLayout.PREFERRED_SIZE)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addComponent(
												wildPokemonPanel,
												javax.swing.GroupLayout.PREFERRED_SIZE,
												javax.swing.GroupLayout.DEFAULT_SIZE,
												javax.swing.GroupLayout.PREFERRED_SIZE)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addComponent(
												staticPokemonPanel,
												javax.swing.GroupLayout.PREFERRED_SIZE,
												javax.swing.GroupLayout.DEFAULT_SIZE,
												javax.swing.GroupLayout.PREFERRED_SIZE)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addComponent(
												tmhmsPanel,
												javax.swing.GroupLayout.PREFERRED_SIZE,
												javax.swing.GroupLayout.DEFAULT_SIZE,
												javax.swing.GroupLayout.PREFERRED_SIZE)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.RELATED)
										.addComponent(
												moveTutorsPanel,
												javax.swing.GroupLayout.PREFERRED_SIZE,
												javax.swing.GroupLayout.DEFAULT_SIZE,
												javax.swing.GroupLayout.PREFERRED_SIZE)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.RELATED)
										.addComponent(
												inGameTradesPanel,
												javax.swing.GroupLayout.PREFERRED_SIZE,
												javax.swing.GroupLayout.DEFAULT_SIZE,
												javax.swing.GroupLayout.PREFERRED_SIZE)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
										.addComponent(
												fieldItemsPanel,
												javax.swing.GroupLayout.PREFERRED_SIZE,
												javax.swing.GroupLayout.DEFAULT_SIZE,
												javax.swing.GroupLayout.PREFERRED_SIZE)
										.addContainerGap(
												javax.swing.GroupLayout.DEFAULT_SIZE,
												Short.MAX_VALUE)));

		optionsScrollPane.setViewportView(optionsContainerPanel);

		openROMButton.setText(bundle
				.getString("RandomizerGUI.openROMButton.text")); // NOI18N
		openROMButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				openROMButtonActionPerformed(evt);
			}
		});

		saveROMButton.setText(bundle
				.getString("RandomizerGUI.saveROMButton.text")); // NOI18N
		saveROMButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				saveROMButtonActionPerformed(evt);
			}
		});

		usePresetsButton.setText(bundle
				.getString("RandomizerGUI.usePresetsButton.text")); // NOI18N
		usePresetsButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				usePresetsButtonActionPerformed(evt);
			}
		});

		aboutButton.setText(bundle.getString("RandomizerGUI.aboutButton.text")); // NOI18N
		aboutButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				aboutButtonActionPerformed(evt);
			}
		});

		otherOptionsPanel
				.setBorder(javax.swing.BorderFactory.createTitledBorder(
						null,
						bundle.getString("RandomizerGUI.otherOptionsPanel.border.title"),
						javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
						javax.swing.border.TitledBorder.DEFAULT_POSITION,
						new java.awt.Font("Tahoma", 1, 11))); // NOI18N

		codeTweaksCB.setText(bundle
				.getString("RandomizerGUI.codeTweaksCB.text")); // NOI18N
		codeTweaksCB.setToolTipText(bundle
				.getString("RandomizerGUI.codeTweaksCB.toolTipText")); // NOI18N
		codeTweaksCB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				codeTweaksCBActionPerformed(evt);
			}
		});

		raceModeCB.setText(bundle.getString("RandomizerGUI.raceModeCB.text")); // NOI18N
		raceModeCB.setToolTipText(bundle
				.getString("RandomizerGUI.raceModeCB.toolTipText")); // NOI18N

		randomizeHollowsCB.setText(bundle
				.getString("RandomizerGUI.randomizeHollowsCB.text")); // NOI18N
		randomizeHollowsCB.setToolTipText(bundle
				.getString("RandomizerGUI.randomizeHollowsCB.toolTipText")); // NOI18N

		brokenMovesCB.setText(bundle
				.getString("RandomizerGUI.brokenMovesCB.text")); // NOI18N
		brokenMovesCB.setToolTipText(bundle
				.getString("RandomizerGUI.brokenMovesCB.toolTipText")); // NOI18N

		codeTweaksBtn.setText(bundle
				.getString("RandomizerGUI.codeTweaksBtn.text")); // NOI18N
		codeTweaksBtn.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				codeTweaksBtnActionPerformed(evt);
			}
		});

		pokeLimitCB.setText(bundle.getString("RandomizerGUI.pokeLimitCB.text")); // NOI18N
		pokeLimitCB.setToolTipText(bundle
				.getString("RandomizerGUI.pokeLimitCB.toolTipText")); // NOI18N
		pokeLimitCB.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				pokeLimitCBActionPerformed(evt);
			}
		});

		pokeLimitBtn.setText(bundle
				.getString("RandomizerGUI.pokeLimitBtn.text")); // NOI18N
		pokeLimitBtn.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				pokeLimitBtnActionPerformed(evt);
			}
		});

		javax.swing.GroupLayout otherOptionsPanelLayout = new javax.swing.GroupLayout(
				otherOptionsPanel);
		otherOptionsPanel.setLayout(otherOptionsPanelLayout);
		otherOptionsPanelLayout
				.setHorizontalGroup(otherOptionsPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								otherOptionsPanelLayout
										.createSequentialGroup()
										.addContainerGap()
										.addGroup(
												otherOptionsPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.LEADING)
														.addComponent(
																raceModeCB)
														.addComponent(
																brokenMovesCB)
														.addGroup(
																otherOptionsPanelLayout
																		.createParallelGroup(
																				javax.swing.GroupLayout.Alignment.TRAILING,
																				false)
																		.addGroup(
																				javax.swing.GroupLayout.Alignment.LEADING,
																				otherOptionsPanelLayout
																						.createSequentialGroup()
																						.addComponent(
																								codeTweaksCB)
																						.addPreferredGap(
																								javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
																						.addComponent(
																								codeTweaksBtn,
																								javax.swing.GroupLayout.DEFAULT_SIZE,
																								javax.swing.GroupLayout.DEFAULT_SIZE,
																								Short.MAX_VALUE))
																		.addGroup(
																				javax.swing.GroupLayout.Alignment.LEADING,
																				otherOptionsPanelLayout
																						.createSequentialGroup()
																						.addComponent(
																								pokeLimitCB)
																						.addPreferredGap(
																								javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
																						.addComponent(
																								pokeLimitBtn))))
										.addContainerGap(
												javax.swing.GroupLayout.DEFAULT_SIZE,
												Short.MAX_VALUE))
						.addGroup(
								javax.swing.GroupLayout.Alignment.TRAILING,
								otherOptionsPanelLayout
										.createSequentialGroup()
										.addContainerGap(
												javax.swing.GroupLayout.DEFAULT_SIZE,
												Short.MAX_VALUE)
										.addComponent(randomizeHollowsCB)
										.addContainerGap()));
		otherOptionsPanelLayout
				.setVerticalGroup(otherOptionsPanelLayout
						.createParallelGroup(
								javax.swing.GroupLayout.Alignment.LEADING)
						.addGroup(
								otherOptionsPanelLayout
										.createSequentialGroup()
										.addGroup(
												otherOptionsPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.LEADING)
														.addComponent(
																codeTweaksCB)
														.addComponent(
																codeTweaksBtn))
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.RELATED)
										.addGroup(
												otherOptionsPanelLayout
														.createParallelGroup(
																javax.swing.GroupLayout.Alignment.LEADING)
														.addComponent(
																pokeLimitBtn)
														.addComponent(
																pokeLimitCB))
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.RELATED)
										.addComponent(raceModeCB)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.RELATED)
										.addComponent(randomizeHollowsCB)
										.addPreferredGap(
												javax.swing.LayoutStyle.ComponentPlacement.RELATED)
										.addComponent(brokenMovesCB)
										.addContainerGap(
												javax.swing.GroupLayout.DEFAULT_SIZE,
												Short.MAX_VALUE)));

		loadQSButton.setText(bundle
				.getString("RandomizerGUI.loadQSButton.text")); // NOI18N
		loadQSButton.setToolTipText(bundle
				.getString("RandomizerGUI.loadQSButton.toolTipText")); // NOI18N
		loadQSButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				loadQSButtonActionPerformed(evt);
			}
		});

		saveQSButton.setText(bundle
				.getString("RandomizerGUI.saveQSButton.text")); // NOI18N
		saveQSButton.setToolTipText(bundle
				.getString("RandomizerGUI.saveQSButton.toolTipText")); // NOI18N
		saveQSButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				saveQSButtonActionPerformed(evt);
			}
		});

		updateSettingsButton.setText(bundle
				.getString("RandomizerGUI.updateSettingsButton.text")); // NOI18N
		updateSettingsButton
				.addActionListener(new java.awt.event.ActionListener() {
					public void actionPerformed(java.awt.event.ActionEvent evt) {
						updateSettingsButtonActionPerformed(evt);
					}
				});

		javax.swing.GroupLayout layout = new javax.swing.GroupLayout(
				getContentPane());
		getContentPane().setLayout(layout);
		layout.setHorizontalGroup(layout
				.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
				.addGroup(
						layout.createSequentialGroup()
								.addContainerGap()
								.addGroup(
										layout.createParallelGroup(
												javax.swing.GroupLayout.Alignment.LEADING)
												.addComponent(
														optionsScrollPane,
														javax.swing.GroupLayout.DEFAULT_SIZE,
														747, Short.MAX_VALUE)
												.addGroup(
														layout.createSequentialGroup()
																.addComponent(
																		generalOptionsPanel,
																		javax.swing.GroupLayout.PREFERRED_SIZE,
																		javax.swing.GroupLayout.DEFAULT_SIZE,
																		javax.swing.GroupLayout.PREFERRED_SIZE)
																.addPreferredGap(
																		javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
																.addComponent(
																		otherOptionsPanel,
																		javax.swing.GroupLayout.PREFERRED_SIZE,
																		javax.swing.GroupLayout.DEFAULT_SIZE,
																		javax.swing.GroupLayout.PREFERRED_SIZE)
																.addPreferredGap(
																		javax.swing.LayoutStyle.ComponentPlacement.RELATED)
																.addGroup(
																		layout.createParallelGroup(
																				javax.swing.GroupLayout.Alignment.LEADING,
																				false)
																				.addGroup(
																						layout.createSequentialGroup()
																								.addComponent(
																										loadQSButton)
																								.addPreferredGap(
																										javax.swing.LayoutStyle.ComponentPlacement.RELATED)
																								.addComponent(
																										saveQSButton))
																				.addComponent(
																						romInfoPanel,
																						javax.swing.GroupLayout.DEFAULT_SIZE,
																						javax.swing.GroupLayout.DEFAULT_SIZE,
																						Short.MAX_VALUE))
																.addGap(18, 18,
																		18)
																.addGroup(
																		layout.createParallelGroup(
																				javax.swing.GroupLayout.Alignment.LEADING)
																				.addComponent(
																						saveROMButton,
																						javax.swing.GroupLayout.Alignment.TRAILING,
																						javax.swing.GroupLayout.DEFAULT_SIZE,
																						147,
																						Short.MAX_VALUE)
																				.addComponent(
																						usePresetsButton,
																						javax.swing.GroupLayout.DEFAULT_SIZE,
																						javax.swing.GroupLayout.DEFAULT_SIZE,
																						Short.MAX_VALUE)
																				.addComponent(
																						openROMButton,
																						javax.swing.GroupLayout.Alignment.TRAILING,
																						javax.swing.GroupLayout.DEFAULT_SIZE,
																						javax.swing.GroupLayout.DEFAULT_SIZE,
																						Short.MAX_VALUE)
																				.addComponent(
																						updateSettingsButton,
																						javax.swing.GroupLayout.DEFAULT_SIZE,
																						javax.swing.GroupLayout.DEFAULT_SIZE,
																						Short.MAX_VALUE)
																				.addComponent(
																						aboutButton,
																						javax.swing.GroupLayout.DEFAULT_SIZE,
																						javax.swing.GroupLayout.DEFAULT_SIZE,
																						Short.MAX_VALUE))))
								.addContainerGap()));
		layout.setVerticalGroup(layout
				.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
				.addGroup(
						layout.createSequentialGroup()
								.addContainerGap()
								.addGroup(
										layout.createParallelGroup(
												javax.swing.GroupLayout.Alignment.LEADING)
												.addComponent(
														otherOptionsPanel,
														javax.swing.GroupLayout.PREFERRED_SIZE,
														145,
														javax.swing.GroupLayout.PREFERRED_SIZE)
												.addGroup(
														layout.createSequentialGroup()
																.addComponent(
																		romInfoPanel,
																		javax.swing.GroupLayout.PREFERRED_SIZE,
																		javax.swing.GroupLayout.DEFAULT_SIZE,
																		javax.swing.GroupLayout.PREFERRED_SIZE)
																.addPreferredGap(
																		javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
																.addGroup(
																		layout.createParallelGroup(
																				javax.swing.GroupLayout.Alignment.LEADING)
																				.addComponent(
																						loadQSButton)
																				.addComponent(
																						saveQSButton)))
												.addGroup(
														layout.createSequentialGroup()
																.addComponent(
																		openROMButton)
																.addPreferredGap(
																		javax.swing.LayoutStyle.ComponentPlacement.RELATED)
																.addComponent(
																		saveROMButton)
																.addPreferredGap(
																		javax.swing.LayoutStyle.ComponentPlacement.RELATED)
																.addComponent(
																		usePresetsButton)
																.addPreferredGap(
																		javax.swing.LayoutStyle.ComponentPlacement.RELATED)
																.addComponent(
																		updateSettingsButton)
																.addPreferredGap(
																		javax.swing.LayoutStyle.ComponentPlacement.RELATED)
																.addComponent(
																		aboutButton))
												.addComponent(
														generalOptionsPanel,
														javax.swing.GroupLayout.PREFERRED_SIZE,
														javax.swing.GroupLayout.DEFAULT_SIZE,
														javax.swing.GroupLayout.PREFERRED_SIZE))
								.addGap(18, 18, 18)
								.addComponent(optionsScrollPane,
										javax.swing.GroupLayout.DEFAULT_SIZE,
										377, Short.MAX_VALUE).addContainerGap()));

		pack();
	}// </editor-fold>//GEN-END:initComponents

	// Variables declaration - do not modify//GEN-BEGIN:variables
	private javax.swing.JPanel abilitiesPanel;
	private javax.swing.JButton aboutButton;
	private javax.swing.JPanel baseStatsPanel;
	private javax.swing.JCheckBox brokenMovesCB;
	private javax.swing.JButton codeTweaksBtn;
	private javax.swing.JCheckBox codeTweaksCB;
	private javax.swing.JCheckBox fiBanBadCB;
	private javax.swing.JRadioButton fiRandomRB;
	private javax.swing.JRadioButton fiShuffleRB;
	private javax.swing.JRadioButton fiUnchangedRB;
	private javax.swing.ButtonGroup fieldItemsButtonGroup;
	private javax.swing.JPanel fieldItemsPanel;
	private javax.swing.JPanel generalOptionsPanel;
	private javax.swing.JCheckBox goCondenseEvosCheckBox;
	private javax.swing.JCheckBox goLowerCaseNamesCheckBox;
	private javax.swing.JCheckBox goNationalDexCheckBox;
	private javax.swing.JCheckBox goRemoveTradeEvosCheckBox;
	private javax.swing.JCheckBox goUpdateMovesCheckBox;
	private javax.swing.JCheckBox goUpdateMovesLegacyCheckBox;
	private javax.swing.JCheckBox goUpdateTypesCheckBox;
	private javax.swing.JRadioButton igtBothRB;
	private javax.swing.JRadioButton igtGivenOnlyRB;
	private javax.swing.JCheckBox igtRandomIVsCB;
	private javax.swing.JCheckBox igtRandomItemCB;
	private javax.swing.JCheckBox igtRandomNicknameCB;
	private javax.swing.JCheckBox igtRandomOTCB;
	private javax.swing.JRadioButton igtUnchangedRB;
	private javax.swing.JPanel inGameTradesPanel;
	private javax.swing.ButtonGroup ingameTradesButtonGroup;
	private javax.swing.JButton loadQSButton;
	private javax.swing.JMenuItem manualUpdateMenuItem;
	private javax.swing.JPanel moveTutorsPanel;
	private javax.swing.JPanel mtCompatPanel;
	private javax.swing.ButtonGroup mtCompatibilityButtonGroup;
	private javax.swing.JCheckBox mtKeepFieldMovesCB;
	private javax.swing.JCheckBox mtLearningSanityCB;
	private javax.swing.ButtonGroup mtMovesButtonGroup;
	private javax.swing.JPanel mtMovesPanel;
	private javax.swing.JLabel mtNoExistLabel;
	private javax.swing.JRadioButton mtcFullRB;
	private javax.swing.JRadioButton mtcRandomTotalRB;
	private javax.swing.JRadioButton mtcRandomTypeRB;
	private javax.swing.JRadioButton mtcUnchangedRB;
	private javax.swing.JRadioButton mtmRandomRB;
	private javax.swing.JRadioButton mtmUnchangedRB;
	private javax.swing.JButton openROMButton;
	private javax.swing.JPanel optionsContainerPanel;
	private javax.swing.JScrollPane optionsScrollPane;
	private javax.swing.JPanel otherOptionsPanel;
	private javax.swing.JRadioButton paRandomizeRB;
	private javax.swing.JRadioButton paUnchangedRB;
	private javax.swing.JCheckBox paWonderGuardCB;
	private javax.swing.JRadioButton pbsChangesRandomEvosRB;
	private javax.swing.JRadioButton pbsChangesRandomTotalRB;
	private javax.swing.JRadioButton pbsChangesShuffleRB;
	private javax.swing.JRadioButton pbsChangesUnchangedRB;
	private javax.swing.JCheckBox pbsStandardEXPCurvesCB;
	private javax.swing.JCheckBox pms4MovesCB;
	private javax.swing.JRadioButton pmsMetronomeOnlyRB;
	private javax.swing.JRadioButton pmsRandomTotalRB;
	private javax.swing.JRadioButton pmsRandomTypeRB;
	private javax.swing.JRadioButton pmsUnchangedRB;
	private javax.swing.ButtonGroup pokeAbilitiesButtonGroup;
	private javax.swing.JButton pokeLimitBtn;
	private javax.swing.JCheckBox pokeLimitCB;
	private javax.swing.ButtonGroup pokeMovesetsButtonGroup;
	private javax.swing.ButtonGroup pokeStatChangesButtonGroup;
	private javax.swing.ButtonGroup pokeTypesButtonGroup;
	private javax.swing.JPanel pokemonMovesetsPanel;
	private javax.swing.JPanel pokemonTypesPanel;
	private javax.swing.JRadioButton ptRandomFollowEvosRB;
	private javax.swing.JRadioButton ptRandomTotalRB;
	private javax.swing.JRadioButton ptUnchangedRB;
	private javax.swing.JFileChooser qsOpenChooser;
	private javax.swing.JFileChooser qsSaveChooser;
	private javax.swing.JCheckBox raceModeCB;
	private javax.swing.JCheckBox randomizeHollowsCB;
	private javax.swing.JLabel riRomCodeLabel;
	private javax.swing.JLabel riRomNameLabel;
	private javax.swing.JLabel riRomSupportLabel;
	private javax.swing.JPanel romInfoPanel;
	private javax.swing.JFileChooser romOpenChooser;
	private javax.swing.JFileChooser romSaveChooser;
	private javax.swing.JButton saveQSButton;
	private javax.swing.JButton saveROMButton;
	private javax.swing.JComboBox spCustomPoke1Chooser;
	private javax.swing.JComboBox spCustomPoke2Chooser;
	private javax.swing.JComboBox spCustomPoke3Chooser;
	private javax.swing.JRadioButton spCustomRB;
	private javax.swing.JCheckBox spHeldItemsBanBadCB;
	private javax.swing.JCheckBox spHeldItemsCB;
	private javax.swing.JRadioButton spRandom2EvosRB;
	private javax.swing.JRadioButton spRandomRB;
	private javax.swing.JRadioButton spUnchangedRB;
	private javax.swing.ButtonGroup starterPokemonButtonGroup;
	private javax.swing.JPanel starterPokemonPanel;
	private javax.swing.ButtonGroup staticPokemonButtonGroup;
	private javax.swing.JPanel staticPokemonPanel;
	private javax.swing.JRadioButton stpRandomL4LRB;
	private javax.swing.JRadioButton stpRandomTotalRB;
	private javax.swing.JRadioButton stpUnchangedRB;
	private javax.swing.JCheckBox tcnRandomizeCB;
	private javax.swing.JRadioButton thcFullRB;
	private javax.swing.JRadioButton thcRandomTotalRB;
	private javax.swing.JRadioButton thcRandomTypeRB;
	private javax.swing.JRadioButton thcUnchangedRB;
	private javax.swing.JPanel tmHmCompatPanel;
	private javax.swing.ButtonGroup tmHmCompatibilityButtonGroup;
	private javax.swing.JCheckBox tmKeepFieldMovesCB;
	private javax.swing.JCheckBox tmLearningSanityCB;
	private javax.swing.ButtonGroup tmMovesButtonGroup;
	private javax.swing.JPanel tmMovesPanel;
	private javax.swing.JPanel tmhmsPanel;
	private javax.swing.JRadioButton tmmRandomRB;
	private javax.swing.JRadioButton tmmUnchangedRB;
	private javax.swing.JCheckBox tnRandomizeCB;
	private javax.swing.JMenuItem toggleAutoUpdatesMenuItem;
	private javax.swing.JCheckBox tpNoEarlyShedinjaCB;
	private javax.swing.JCheckBox tpNoLegendariesCB;
	private javax.swing.JCheckBox tpPowerLevelsCB;
	private javax.swing.JRadioButton tpRandomRB;
	private javax.swing.JCheckBox tpRivalCarriesStarterCB;
	private javax.swing.JRadioButton tpTypeThemedRB;
	private javax.swing.JCheckBox tpTypeWeightingCB;
	private javax.swing.JRadioButton tpUnchangedRB;
	private javax.swing.ButtonGroup trainerPokesButtonGroup;
	private javax.swing.JPanel trainersPokemonPanel;
	private javax.swing.JButton updateSettingsButton;
	private javax.swing.JPopupMenu updateSettingsMenu;
	private javax.swing.JButton usePresetsButton;
	private javax.swing.JPanel wildPokemonARulePanel;
	private javax.swing.JPanel wildPokemonPanel;
	private javax.swing.ButtonGroup wildPokesARuleButtonGroup;
	private javax.swing.ButtonGroup wildPokesButtonGroup;
	private javax.swing.JRadioButton wpARCatchEmAllRB;
	private javax.swing.JRadioButton wpARNoneRB;
	private javax.swing.JRadioButton wpARSimilarStrengthRB;
	private javax.swing.JRadioButton wpARTypeThemedRB;
	private javax.swing.JRadioButton wpArea11RB;
	private javax.swing.JCheckBox wpCatchRateCB;
	private javax.swing.JRadioButton wpGlobalRB;
	private javax.swing.JCheckBox wpHeldItemsBanBadCB;
	private javax.swing.JCheckBox wpHeldItemsCB;
	private javax.swing.JCheckBox wpNoLegendariesCB;
	private javax.swing.JRadioButton wpRandomRB;
	private javax.swing.JRadioButton wpUnchangedRB;
	private javax.swing.JCheckBox wpUseTimeCB;
	// End of variables declaration//GEN-END:variables
}
