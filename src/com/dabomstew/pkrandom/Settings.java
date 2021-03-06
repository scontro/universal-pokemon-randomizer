package com.dabomstew.pkrandom;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.zip.CRC32;

import javax.xml.bind.DatatypeConverter;

import com.dabomstew.pkrandom.pokemon.GenRestrictions;
import com.dabomstew.pkrandom.pokemon.Pokemon;
import com.dabomstew.pkrandom.romhandlers.Gen1RomHandler;
import com.dabomstew.pkrandom.romhandlers.Gen2RomHandler;
import com.dabomstew.pkrandom.romhandlers.Gen3RomHandler;
import com.dabomstew.pkrandom.romhandlers.Gen5RomHandler;
import com.dabomstew.pkrandom.romhandlers.RomHandler;

public class Settings {

	public static final int VERSION = 163;

	public static final int LENGTH_OF_SETTINGS_DATA = 28;

	private byte[] trainerClasses;
	private byte[] trainerNames;
	private byte[] nicknames;

	private String romName;
	private boolean updatedFromOldVersion = false;
	private GenRestrictions currentRestrictions;
	private int currentCodeTweaks;

	private boolean updateTypeEffectiveness;
	private boolean updateMoves;
	private boolean updateMovesLegacy;
	private boolean changeImpossibleEvolutions;
	private boolean makeEvolutionsEasier;
	private boolean lowerCasePokemonNames;
	private boolean nationalDexAtStart;
	private boolean raceMode;
	private boolean randomizeHiddenHollows;
	private boolean useCodeTweaks;
	private boolean blockBrokenMoves;
	private boolean limitPokemon;

	public enum BaseStatisticsMod {
		UNCHANGED, SHUFFLE, RANDOM_FOLLOW_EVOLUTIONS, COMPLETELY_RANDOM,
	}

	private BaseStatisticsMod baseStatisticsMod = BaseStatisticsMod.UNCHANGED;
	private boolean standardizeEXPCurves;

	public enum AbilitiesMod {
		UNCHANGED, RANDOMIZE
	}

	private AbilitiesMod abilitiesMod = AbilitiesMod.UNCHANGED;
	private boolean allowWonderGuard = true;

	public enum StartersMod {
		UNCHANGED, CUSTOM, COMPLETELY_RANDOM, RANDOM_WITH_TWO_EVOLUTIONS
	}

	private StartersMod startersMod = StartersMod.UNCHANGED;

	// index in the rom's list of pokemon
	// offset from the dropdown index from RandomizerGUI by 1
	private int[] customStarters = new int[3];
	private boolean randomizeStartersHeldItems;
	private boolean banBadRandomStarterHeldItems;

	public enum TypesMod {
		UNCHANGED, RANDOM_FOLLOW_EVOLUTIONS, COMPLETELY_RANDOM
	}

	private TypesMod typesMod = TypesMod.UNCHANGED;

	public enum MovesetsMod {
		UNCHANGED, RANDOM_PREFER_SAME_TYPE, COMPLETELY_RANDOM, METRONOME_ONLY
	}

	private MovesetsMod movesetsMod = MovesetsMod.UNCHANGED;
	private boolean startWithFourMoves;

	public enum TrainersMod {
		UNCHANGED, RANDOM, TYPE_THEMED
	}

	private TrainersMod trainersMod = TrainersMod.UNCHANGED;
	private boolean rivalCarriesStarterThroughout;
	private boolean trainersUsePokemonOfSimilarStrength;
	private boolean trainersMatchTypingDistribution;
	private boolean trainersBlockLegendaries = true;
	private boolean trainersBlockEarlyWonderGuard = true;
	private boolean randomizeTrainerNames;
	private boolean randomizeTrainerClassNames;

	public enum WildPokemonMod {
		UNCHANGED, RANDOM, AREA_MAPPING, GLOBAL_MAPPING
	}

	public enum WildPokemonRestrictionMod {
		NONE, SIMILAR_STRENGTH, CATCH_EM_ALL, TYPE_THEME_AREAS
	}

	private WildPokemonMod wildPokemonMod = WildPokemonMod.UNCHANGED;
	private WildPokemonRestrictionMod wildPokemonRestrictionMod = WildPokemonRestrictionMod.NONE;
	private boolean useTimeBasedEncounters;
	private boolean blockWildLegendaries = true;
	private boolean useMinimumCatchRate;
	private boolean randomizeWildPokemonHeldItems;
	private boolean banBadRandomWildPokemonHeldItems;

	public enum StaticPokemonMod {
		UNCHANGED, RANDOM_MATCHING, COMPLETELY_RANDOM
	}

	private StaticPokemonMod staticPokemonMod = StaticPokemonMod.UNCHANGED;

	public enum TMsMod {
		UNCHANGED, RANDOM
	}

	private TMsMod tmsMod = TMsMod.UNCHANGED;
	private boolean tmLevelUpMoveSanity;
	private boolean keepFieldMoveTMs;

	public enum TMsHMsCompatibilityMod {
		UNCHANGED, RANDOM_PREFER_TYPE, COMPLETELY_RANDOM, FULL
	}

	private TMsHMsCompatibilityMod tmsHmsCompatibilityMod = TMsHMsCompatibilityMod.UNCHANGED;

	public enum MoveTutorMovesMod {
		UNCHANGED, RANDOM
	}

	private MoveTutorMovesMod moveTutorMovesMod = MoveTutorMovesMod.UNCHANGED;
	private boolean tutorLevelUpMoveSanity;
	private boolean keepFieldMoveTutors;

	public enum MoveTutorsCompatibilityMod {
		UNCHANGED, RANDOM_PREFER_TYPE, COMPLETELY_RANDOM, FULL
	}

	private MoveTutorsCompatibilityMod moveTutorsCompatibilityMod = MoveTutorsCompatibilityMod.UNCHANGED;

	public enum InGameTradesMod {
		UNCHANGED, RANDOMIZE_GIVEN, RANDOMIZE_GIVEN_AND_REQUESTED
	}

	private InGameTradesMod inGameTradesMod = InGameTradesMod.UNCHANGED;
	private boolean randomizeInGameTradesNicknames;
	private boolean randomizeInGameTradesOTs;
	private boolean randomizeInGameTradesIVs;
	private boolean randomizeInGameTradesItems;

	public enum FieldItemsMod {
		UNCHANGED, SHUFFLE, RANDOM
	}

	private FieldItemsMod fieldItemsMod = FieldItemsMod.UNCHANGED;
	private boolean banBadRandomFieldItems;

	// to and from strings etc
	public void write(FileOutputStream out) throws IOException {
		out.write(VERSION);
		byte[] settings = toString().getBytes("UTF-8");
		out.write(settings.length);
		out.write(settings);
	}

	public static Settings read(FileInputStream in) throws IOException,
			UnsupportedOperationException {
		int version = in.read();
		if (version > VERSION) {
			throw new UnsupportedOperationException(
					"Cannot read settings from a newer version of the randomizer.");
		}
		int length = in.read();
		byte[] buffer = new byte[length];
		in.read(buffer);
		String settings = new String(buffer, "UTF-8");
		boolean oldUpdate = false;

		if (version < VERSION) {
			oldUpdate = true;
			settings = new SettingsUpdater().update(version, settings);
		}

		Settings settingsObj = fromString(settings);
		settingsObj.setUpdatedFromOldVersion(oldUpdate);
		return settingsObj;
	}

	@Override
	public String toString() {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		// 0: general options #1 + trainer/class names
		out.write(makeByteSelected(lowerCasePokemonNames, nationalDexAtStart,
				changeImpossibleEvolutions, updateMoves, updateMovesLegacy,
				updateTypeEffectiveness, randomizeTrainerNames,
				randomizeTrainerClassNames));

		// 1: pokemon base stats & abilities
		out.write(makeByteSelected(
				baseStatisticsMod == BaseStatisticsMod.RANDOM_FOLLOW_EVOLUTIONS,
				baseStatisticsMod == BaseStatisticsMod.COMPLETELY_RANDOM,
				baseStatisticsMod == BaseStatisticsMod.SHUFFLE,
				baseStatisticsMod == BaseStatisticsMod.UNCHANGED,
				abilitiesMod == AbilitiesMod.UNCHANGED,
				abilitiesMod == AbilitiesMod.RANDOMIZE, allowWonderGuard,
				standardizeEXPCurves));

		// 2: pokemon types & more general options
		out.write(makeByteSelected(
				typesMod == TypesMod.RANDOM_FOLLOW_EVOLUTIONS,
				typesMod == TypesMod.COMPLETELY_RANDOM,
				typesMod == TypesMod.UNCHANGED, useCodeTweaks, raceMode,
				randomizeHiddenHollows, blockBrokenMoves, limitPokemon));

		// v162: 3: new general options byte added (the rest were full)

		out.write(makeByteSelected(makeEvolutionsEasier));

		// 4: starter pokemon stuff
		out.write(makeByteSelected(startersMod == StartersMod.CUSTOM,
				startersMod == StartersMod.COMPLETELY_RANDOM,
				startersMod == StartersMod.UNCHANGED,
				startersMod == StartersMod.RANDOM_WITH_TWO_EVOLUTIONS,
				randomizeStartersHeldItems, banBadRandomStarterHeldItems));

		// @5 dropdowns
		write2ByteInt(out, customStarters[0] - 1);
		write2ByteInt(out, customStarters[1] - 1);
		write2ByteInt(out, customStarters[2] - 1);

		// 11 movesets
		out.write(makeByteSelected(
				movesetsMod == MovesetsMod.COMPLETELY_RANDOM,
				movesetsMod == MovesetsMod.RANDOM_PREFER_SAME_TYPE,
				movesetsMod == MovesetsMod.UNCHANGED,
				movesetsMod == MovesetsMod.METRONOME_ONLY, startWithFourMoves));

		// 12 trainer pokemon
		// changed 160
		out.write(makeByteSelected(trainersUsePokemonOfSimilarStrength,
				trainersMod == TrainersMod.RANDOM,
				rivalCarriesStarterThroughout,
				trainersMod == TrainersMod.TYPE_THEMED,
				trainersMatchTypingDistribution,
				trainersMod == TrainersMod.UNCHANGED, trainersBlockLegendaries,
				trainersBlockEarlyWonderGuard));

		// 13 wild pokemon
		out.write(makeByteSelected(
				wildPokemonRestrictionMod == WildPokemonRestrictionMod.CATCH_EM_ALL,
				wildPokemonMod == WildPokemonMod.AREA_MAPPING,
				wildPokemonRestrictionMod == WildPokemonRestrictionMod.NONE,
				wildPokemonRestrictionMod == WildPokemonRestrictionMod.TYPE_THEME_AREAS,
				wildPokemonMod == WildPokemonMod.GLOBAL_MAPPING,
				wildPokemonMod == WildPokemonMod.RANDOM,
				wildPokemonMod == WildPokemonMod.UNCHANGED,
				useTimeBasedEncounters));

		// 14 wild pokemon 2
		// bugfix 161
		out.write(makeByteSelected(
				useMinimumCatchRate,
				!blockWildLegendaries,
				wildPokemonRestrictionMod == WildPokemonRestrictionMod.SIMILAR_STRENGTH,
				randomizeWildPokemonHeldItems, banBadRandomWildPokemonHeldItems));

		// 15 static pokemon
		out.write(makeByteSelected(
				staticPokemonMod == StaticPokemonMod.UNCHANGED,
				staticPokemonMod == StaticPokemonMod.RANDOM_MATCHING,
				staticPokemonMod == StaticPokemonMod.COMPLETELY_RANDOM));

		// 16 tm randomization
		// new stuff 162
		out.write(makeByteSelected(
				tmsHmsCompatibilityMod == TMsHMsCompatibilityMod.COMPLETELY_RANDOM,
				tmsHmsCompatibilityMod == TMsHMsCompatibilityMod.RANDOM_PREFER_TYPE,
				tmsHmsCompatibilityMod == TMsHMsCompatibilityMod.UNCHANGED,
				tmsMod == TMsMod.RANDOM, tmsMod == TMsMod.UNCHANGED,
				tmLevelUpMoveSanity, keepFieldMoveTMs,
				tmsHmsCompatibilityMod == TMsHMsCompatibilityMod.FULL));

		// 17 move tutor randomization
		out.write(makeByteSelected(
				moveTutorsCompatibilityMod == MoveTutorsCompatibilityMod.COMPLETELY_RANDOM,
				moveTutorsCompatibilityMod == MoveTutorsCompatibilityMod.RANDOM_PREFER_TYPE,
				moveTutorsCompatibilityMod == MoveTutorsCompatibilityMod.UNCHANGED,
				moveTutorMovesMod == MoveTutorMovesMod.RANDOM,
				moveTutorMovesMod == MoveTutorMovesMod.UNCHANGED,
				tutorLevelUpMoveSanity, keepFieldMoveTutors,
				moveTutorsCompatibilityMod == MoveTutorsCompatibilityMod.FULL));

		// new 150
		// 18 in game trades
		out.write(makeByteSelected(
				inGameTradesMod == InGameTradesMod.RANDOMIZE_GIVEN_AND_REQUESTED,
				inGameTradesMod == InGameTradesMod.RANDOMIZE_GIVEN,
				randomizeInGameTradesItems, randomizeInGameTradesIVs,
				randomizeInGameTradesNicknames, randomizeInGameTradesOTs,
				inGameTradesMod == InGameTradesMod.UNCHANGED));

		// 19 field items
		out.write(makeByteSelected(fieldItemsMod == FieldItemsMod.RANDOM,
				fieldItemsMod == FieldItemsMod.SHUFFLE,
				fieldItemsMod == FieldItemsMod.UNCHANGED,
				banBadRandomFieldItems));

		// @ 20 pokemon restrictions
		try {
			if (currentRestrictions != null) {
				writeFullInt(out, currentRestrictions.toInt());
			} else {
				writeFullInt(out, 0);
			}
		} catch (IOException e) {
		}

		// @ 24 code tweaks
		try {
			writeFullInt(out, currentCodeTweaks);
		} catch (IOException e) {

		}

		try {
			byte[] romName = this.romName.getBytes("US-ASCII");
			out.write(romName.length);
			out.write(romName);
		} catch (UnsupportedEncodingException e) {
			out.write(0);
		} catch (IOException e) {
			out.write(0);
		}

		byte[] current = out.toByteArray();
		CRC32 checksum = new CRC32();
		checksum.update(current);

		try {
			writeFullInt(out, (int) checksum.getValue());
			writeFullInt(out,
					FileFunctions.getFileChecksum("trainerclasses.txt"));
			writeFullInt(out, FileFunctions.getFileChecksum("trainernames.txt"));
			writeFullInt(out, FileFunctions.getFileChecksum("nicknames.txt"));
		} catch (IOException e) {
		}

		return DatatypeConverter.printBase64Binary(out.toByteArray());
	}

	public static Settings fromString(String settingsString)
			throws UnsupportedEncodingException {
		byte[] data = DatatypeConverter.parseBase64Binary(settingsString);
		checkChecksum(data);

		Settings settings = new Settings();

		// Restore the actual controls
		settings.setLowerCasePokemonNames(restoreState(data[0], 0));
		settings.setNationalDexAtStart(restoreState(data[0], 1));
		settings.setChangeImpossibleEvolutions(restoreState(data[0], 2));
		settings.setUpdateMoves(restoreState(data[0], 3));
		settings.setUpdateMovesLegacy(restoreState(data[0], 4));
		settings.setUpdateTypeEffectiveness(restoreState(data[0], 5));
		settings.setRandomizeTrainerNames(restoreState(data[0], 6));
		settings.setRandomizeTrainerClassNames(restoreState(data[0], 7));

		settings.setBaseStatisticsMod(restoreEnum(BaseStatisticsMod.class,
				data[1], 3, // UNCHANGED
				2, // SHUFFLE
				0, // RANDOM_FOLLOW_EVOLUTIONS
				1 // COMPLETELY_RANDOM
		));
		settings.setAbilitiesMod(restoreEnum(AbilitiesMod.class, data[1], 4, // UNCHANGED
				5 // RANDOMIZE
		));
		settings.setAllowWonderGuard(restoreState(data[1], 6));
		settings.setStandardizeEXPCurves(restoreState(data[1], 7));

		settings.setTypesMod(restoreEnum(TypesMod.class, data[2], 2, // UNCHANGED
				0, // RANDOM_FOLLOW_EVOLUTIONS
				1 // COMPLETELY_RANDOM
		));
		settings.setUseCodeTweaks(restoreState(data[2], 3));
		settings.setRaceMode(restoreState(data[2], 4));
		settings.setRandomizeHiddenHollows(restoreState(data[2], 5));
		settings.setBlockBrokenMoves(restoreState(data[2], 6));
		settings.setLimitPokemon(restoreState(data[2], 7));

		settings.setMakeEvolutionsEasier(restoreState(data[3], 0));

		settings.setStartersMod(restoreEnum(StartersMod.class, data[4], 2, // UNCHANGED
				0, // CUSTOM
				1, // COMPLETELY_RANDOM
				3 // RANDOM_WITH_TWO_EVOLUTIONS
		));
		settings.setRandomizeStartersHeldItems(restoreState(data[4], 4));
		settings.setBanBadRandomStarterHeldItems(restoreState(data[4], 5));

		settings.setCustomStarters(new int[] {
				FileFunctions.read2ByteInt(data, 5) + 1,
				FileFunctions.read2ByteInt(data, 7) + 1,
				FileFunctions.read2ByteInt(data, 9) + 1 });

		settings.setMovesetsMod(restoreEnum(MovesetsMod.class, data[11], 2, // UNCHANGED
				1, // RANDOM_PREFER_SAME_TYPE
				0, // COMPLETELY_RANDOM
				3 // METRONOME_ONLY
		));
		settings.setStartWithFourMoves(restoreState(data[11], 4));

		// changed 160
		settings.setTrainersMod(restoreEnum(TrainersMod.class, data[12], 5, // UNCHANGED
				1, // RANDOM
				3 // TYPE_THEMED
		));
		settings.setTrainersUsePokemonOfSimilarStrength(restoreState(data[12],
				0));
		settings.setRivalCarriesStarterThroughout(restoreState(data[12], 2));
		settings.setTrainersMatchTypingDistribution(restoreState(data[12], 4));
		settings.setTrainersBlockLegendaries(restoreState(data[12], 6));
		settings.setTrainersBlockEarlyWonderGuard(restoreState(data[12], 7));

		settings.setWildPokemonMod(restoreEnum(WildPokemonMod.class, data[13],
				6, // UNCHANGED
				5, // RANDOM
				1, // AREA_MAPPING
				4 // GLOBAL_MAPPING
		));
		settings.setWildPokemonRestrictionMod(getEnum(
				WildPokemonRestrictionMod.class, restoreState(data[13], 2), // NONE
				restoreState(data[14], 2), // SIMILAR_STRENGTH
				restoreState(data[13], 0), // CATCH_EM_ALL
				restoreState(data[13], 3) // TYPE_THEME_AREAS
		));
		settings.setUseTimeBasedEncounters(restoreState(data[13], 7));

		settings.setUseMinimumCatchRate(restoreState(data[14], 0));
		settings.setBlockWildLegendaries(restoreState(data[14], 1));
		settings.setRandomizeWildPokemonHeldItems(restoreState(data[14], 3));
		settings.setBanBadRandomWildPokemonHeldItems(restoreState(data[14], 4));

		settings.setStaticPokemonMod(restoreEnum(StaticPokemonMod.class,
				data[15], 0, // UNCHANGED
				1, // RANDOM_MATCHING
				2 // COMPLETELY_RANDOM
		));

		settings.setTmsMod(restoreEnum(TMsMod.class, data[16], 4, // UNCHANGED
				3 // RANDOM
		));
		settings.setTmsHmsCompatibilityMod(restoreEnum(
				TMsHMsCompatibilityMod.class, data[16], 2, // UNCHANGED
				1, // RANDOM_PREFER_TYPE
				0, // COMPLETELY_RANDOM
				7 // FULL
		));
		settings.setTmLevelUpMoveSanity(restoreState(data[16], 5));
		settings.setKeepFieldMoveTMs(restoreState(data[16], 6));

		settings.setMoveTutorMovesMod(restoreEnum(MoveTutorMovesMod.class,
				data[17], 4, // UNCHANGED
				3 // RANDOM
		));
		settings.setMoveTutorsCompatibilityMod(restoreEnum(
				MoveTutorsCompatibilityMod.class, data[17], 2, // UNCHANGED
				1, // RANDOM_PREFER_TYPE
				0, // COMPLETELY_RANDOM
				7 // FULL
		));
		settings.setTutorLevelUpMoveSanity(restoreState(data[17], 5));
		settings.setKeepFieldMoveTutors(restoreState(data[17], 6));

		// new 150
		settings.setInGameTradesMod(restoreEnum(InGameTradesMod.class,
				data[18], 6, // UNCHANGED
				1, // RANDOMIZE_GIVEN
				0 // RANDOMIZE_GIVEN_AND_REQUESTED
		));
		settings.setRandomizeInGameTradesItems(restoreState(data[18], 2));
		settings.setRandomizeInGameTradesIVs(restoreState(data[18], 3));
		settings.setRandomizeInGameTradesNicknames(restoreState(data[18], 4));
		settings.setRandomizeInGameTradesOTs(restoreState(data[18], 5));

		settings.setFieldItemsMod(restoreEnum(FieldItemsMod.class, data[19], 2, // UNCHANGED
				1, // SHUFFLE
				0 // RANDOM
		));
		settings.setBanBadRandomFieldItems(restoreState(data[19], 3));

		// gen restrictions
		int genLimit = FileFunctions.readFullInt(data, 20);
		GenRestrictions restrictions = null;
		if (genLimit != 0) {
			restrictions = new GenRestrictions(genLimit);
		}
		settings.setCurrentRestrictions(restrictions);

		int codeTweaks = FileFunctions.readFullInt(data, 24);

		// Sanity override
		if (codeTweaks == 0) {
			settings.setUseCodeTweaks(false);
		}
		settings.setCurrentCodeTweaks(codeTweaks);

		int romNameLength = data[LENGTH_OF_SETTINGS_DATA] & 0xFF;
		String romName = new String(data, LENGTH_OF_SETTINGS_DATA + 1,
				romNameLength, "US-ASCII");
		settings.setRomName(romName);

		return settings;
	}

	public static class TweakForROMFeedback {
		private boolean changedStarter;
		private boolean removedCodeTweaks;

		public boolean isChangedStarter() {
			return changedStarter;
		}

		public TweakForROMFeedback setChangedStarter(boolean changedStarter) {
			this.changedStarter = changedStarter;
			return this;
		}

		public boolean isRemovedCodeTweaks() {
			return removedCodeTweaks;
		}

		public TweakForROMFeedback setRemovedCodeTweaks(
				boolean removedCodeTweaks) {
			this.removedCodeTweaks = removedCodeTweaks;
			return this;
		}
	}

	public TweakForROMFeedback tweakForRom(RomHandler rh) {

		TweakForROMFeedback feedback = new TweakForROMFeedback();

		// move update check
		if (this.isUpdateMovesLegacy() && rh instanceof Gen5RomHandler) {
			// don't actually update moves
			this.setUpdateMovesLegacy(false);
			this.setUpdateMoves(false);
		}

		// starters
		List<Pokemon> romPokemon = rh.getPokemon();
		List<Pokemon> romStarters = rh.getStarters();
		for (int starter = 0; starter < 3; starter++) {
			if (this.customStarters[starter] < 0
					|| this.customStarters[starter] >= romPokemon.size()) {
				// invalid starter for this game
				feedback.setChangedStarter(true);
				if (starter >= romStarters.size()) {
					this.customStarters[starter] = 1;
				} else {
					this.customStarters[starter] = romPokemon
							.indexOf(romStarters.get(starter));
				}
			}
		}

		// gen restrictions
		if (rh instanceof Gen1RomHandler || rh.isROMHack()) {
			this.currentRestrictions = null;
			this.setLimitPokemon(false);
		} else if (this.currentRestrictions != null) {
			this.currentRestrictions.limitToGen(rh.generationOfPokemon());
		}

		// code tweaks
		int oldCodeTweaks = this.currentCodeTweaks;
		this.currentCodeTweaks &= rh.codeTweaksAvailable();

		if (oldCodeTweaks != this.currentCodeTweaks) {
			feedback.setRemovedCodeTweaks(true);
		}

		if (this.currentCodeTweaks == 0) {
			this.setUseCodeTweaks(false);
		}

		// checkboxes that are only used for certain gens
		if (!(rh instanceof Gen1RomHandler)) {
			this.setUpdateTypeEffectiveness(false);
		}

		if (rh instanceof Gen5RomHandler) {
			this.setLowerCasePokemonNames(false);
		}

		if (!(rh instanceof Gen3RomHandler)) {
			this.setNationalDexAtStart(false);
		}

		if (!rh.hasHiddenHollowPokemon()) {
			this.setRandomizeHiddenHollows(false);
		}

		if (rh.abilitiesPerPokemon() == 0) {
			this.setAbilitiesMod(AbilitiesMod.UNCHANGED);
			this.setAllowWonderGuard(false);
		}

		if (!(rh instanceof Gen2RomHandler || rh instanceof Gen3RomHandler)) {
			// starter held items don't exist
			this.setRandomizeStartersHeldItems(false);
			this.setBanBadRandomStarterHeldItems(false);
		}

		if (!rh.supportsFourStartingMoves()) {
			this.setStartWithFourMoves(false);
		}

		if (rh instanceof Gen1RomHandler || rh instanceof Gen2RomHandler) {
			this.setTrainersBlockEarlyWonderGuard(false);
		}

		if (!rh.hasTimeBasedEncounters()) {
			this.setUseTimeBasedEncounters(false);
		}

		if (rh instanceof Gen1RomHandler) {
			this.setRandomizeWildPokemonHeldItems(false);
			this.setBanBadRandomWildPokemonHeldItems(false);
		}

		if (!rh.canChangeStaticPokemon()) {
			this.setStaticPokemonMod(StaticPokemonMod.UNCHANGED);
		}

		if (!rh.hasMoveTutors()) {
			this.setMoveTutorMovesMod(MoveTutorMovesMod.UNCHANGED);
			this.setMoveTutorsCompatibilityMod(MoveTutorsCompatibilityMod.UNCHANGED);
			this.setTutorLevelUpMoveSanity(false);
			this.setKeepFieldMoveTutors(false);
		}

		if (rh instanceof Gen1RomHandler) {
			// missing some ingame trade fields
			this.setRandomizeInGameTradesItems(false);
			this.setRandomizeInGameTradesIVs(false);
			this.setRandomizeInGameTradesOTs(false);
		}

		// done
		return feedback;
	}

	// getters and setters

	public byte[] getTrainerClasses() {
		return trainerClasses;
	}

	public Settings setTrainerClasses(byte[] trainerClasses) {
		this.trainerClasses = trainerClasses;
		return this;
	}

	public byte[] getTrainerNames() {
		return trainerNames;
	}

	public Settings setTrainerNames(byte[] trainerNames) {
		this.trainerNames = trainerNames;
		return this;
	}

	public byte[] getNicknames() {
		return nicknames;
	}

	public Settings setNicknames(byte[] nicknames) {
		this.nicknames = nicknames;
		return this;
	}

	public String getRomName() {
		return romName;
	}

	public Settings setRomName(String romName) {
		this.romName = romName;
		return this;
	}

	public boolean isUpdatedFromOldVersion() {
		return updatedFromOldVersion;
	}

	public Settings setUpdatedFromOldVersion(boolean updatedFromOldVersion) {
		this.updatedFromOldVersion = updatedFromOldVersion;
		return this;
	}

	public GenRestrictions getCurrentRestrictions() {
		return currentRestrictions;
	}

	public Settings setCurrentRestrictions(GenRestrictions currentRestrictions) {
		this.currentRestrictions = currentRestrictions;
		return this;
	}

	public int getCurrentCodeTweaks() {
		return currentCodeTweaks;
	}

	public Settings setCurrentCodeTweaks(int currentCodeTweaks) {
		this.currentCodeTweaks = currentCodeTweaks;
		return this;
	}

	public boolean isUpdateTypeEffectiveness() {
		return updateTypeEffectiveness;
	}

	public Settings setUpdateTypeEffectiveness(boolean updateTypeEffectiveness) {
		this.updateTypeEffectiveness = updateTypeEffectiveness;
		return this;
	}

	public boolean isUpdateMoves() {
		return updateMoves;
	}

	public Settings setUpdateMoves(boolean updateMoves) {
		this.updateMoves = updateMoves;
		return this;
	}

	public boolean isUpdateMovesLegacy() {
		return updateMovesLegacy;
	}

	public Settings setUpdateMovesLegacy(boolean updateMovesLegacy) {
		this.updateMovesLegacy = updateMovesLegacy;
		return this;
	}

	public boolean isChangeImpossibleEvolutions() {
		return changeImpossibleEvolutions;
	}

	public Settings setChangeImpossibleEvolutions(
			boolean changeImpossibleEvolutions) {
		this.changeImpossibleEvolutions = changeImpossibleEvolutions;
		return this;
	}

	public boolean isMakeEvolutionsEasier() {
		return makeEvolutionsEasier;
	}

	public Settings setMakeEvolutionsEasier(boolean makeEvolutionsEasier) {
		this.makeEvolutionsEasier = makeEvolutionsEasier;
		return this;
	}

	public boolean isLowerCasePokemonNames() {
		return lowerCasePokemonNames;
	}

	public Settings setLowerCasePokemonNames(boolean lowerCasePokemonNames) {
		this.lowerCasePokemonNames = lowerCasePokemonNames;
		return this;
	}

	public boolean isNationalDexAtStart() {
		return nationalDexAtStart;
	}

	public Settings setNationalDexAtStart(boolean nationalDexAtStart) {
		this.nationalDexAtStart = nationalDexAtStart;
		return this;
	}

	public boolean isRaceMode() {
		return raceMode;
	}

	public Settings setRaceMode(boolean raceMode) {
		this.raceMode = raceMode;
		return this;
	}

	public boolean isRandomizeHiddenHollows() {
		return randomizeHiddenHollows;
	}

	public Settings setRandomizeHiddenHollows(boolean randomizeHiddenHollows) {
		this.randomizeHiddenHollows = randomizeHiddenHollows;
		return this;
	}

	public boolean isUseCodeTweaks() {
		return useCodeTweaks;
	}

	public Settings setUseCodeTweaks(boolean useCodeTweaks) {
		this.useCodeTweaks = useCodeTweaks;
		return this;
	}

	public boolean doBlockBrokenMoves() {
		return blockBrokenMoves;
	}

	public Settings setBlockBrokenMoves(boolean blockBrokenMoves) {
		this.blockBrokenMoves = blockBrokenMoves;
		return this;
	}

	public boolean isLimitPokemon() {
		return limitPokemon;
	}

	public Settings setLimitPokemon(boolean limitPokemon) {
		this.limitPokemon = limitPokemon;
		return this;
	}

	public BaseStatisticsMod getBaseStatisticsMod() {
		return baseStatisticsMod;
	}

	public Settings setBaseStatisticsMod(BaseStatisticsMod baseStatisticsMod) {
		this.baseStatisticsMod = baseStatisticsMod;
		return this;
	}

	public Settings setBaseStatisticsMod(boolean... bools) {
		return setBaseStatisticsMod(getEnum(BaseStatisticsMod.class, bools));
	}

	public boolean isStandardizeEXPCurves() {
		return standardizeEXPCurves;
	}

	public Settings setStandardizeEXPCurves(boolean standardizeEXPCurves) {
		this.standardizeEXPCurves = standardizeEXPCurves;
		return this;
	}

	public AbilitiesMod getAbilitiesMod() {
		return abilitiesMod;
	}

	public Settings setAbilitiesMod(AbilitiesMod abilitiesMod) {
		this.abilitiesMod = abilitiesMod;
		return this;
	}

	public Settings setAbilitiesMod(boolean... bools) {
		return setAbilitiesMod(getEnum(AbilitiesMod.class, bools));
	}

	public boolean isAllowWonderGuard() {
		return allowWonderGuard;
	}

	public Settings setAllowWonderGuard(boolean allowWonderGuard) {
		this.allowWonderGuard = allowWonderGuard;
		return this;
	}

	public StartersMod getStartersMod() {
		return startersMod;
	}

	public Settings setStartersMod(StartersMod startersMod) {
		this.startersMod = startersMod;
		return this;
	}

	public Settings setStartersMod(boolean... bools) {
		return setStartersMod(getEnum(StartersMod.class, bools));
	}

	public int[] getCustomStarters() {
		return customStarters;
	}

	public Settings setCustomStarters(int[] customStarters) {
		this.customStarters = customStarters;
		return this;
	}

	public boolean isRandomizeStartersHeldItems() {
		return randomizeStartersHeldItems;
	}

	public Settings setRandomizeStartersHeldItems(
			boolean randomizeStartersHeldItems) {
		this.randomizeStartersHeldItems = randomizeStartersHeldItems;
		return this;
	}

	public boolean isBanBadRandomStarterHeldItems() {
		return banBadRandomStarterHeldItems;
	}

	public Settings setBanBadRandomStarterHeldItems(
			boolean banBadRandomStarterHeldItems) {
		this.banBadRandomStarterHeldItems = banBadRandomStarterHeldItems;
		return this;
	}

	public TypesMod getTypesMod() {
		return typesMod;
	}

	public Settings setTypesMod(TypesMod typesMod) {
		this.typesMod = typesMod;
		return this;
	}

	public Settings setTypesMod(boolean... bools) {
		return setTypesMod(getEnum(TypesMod.class, bools));
	}

	public MovesetsMod getMovesetsMod() {
		return movesetsMod;
	}

	public Settings setMovesetsMod(MovesetsMod movesetsMod) {
		this.movesetsMod = movesetsMod;
		return this;
	}

	public Settings setMovesetsMod(boolean... bools) {
		return setMovesetsMod(getEnum(MovesetsMod.class, bools));
	}

	public boolean isStartWithFourMoves() {
		return startWithFourMoves;
	}

	public Settings setStartWithFourMoves(boolean startWithFourMoves) {
		this.startWithFourMoves = startWithFourMoves;
		return this;
	}

	public TrainersMod getTrainersMod() {
		return trainersMod;
	}

	public Settings setTrainersMod(TrainersMod trainersMod) {
		this.trainersMod = trainersMod;
		return this;
	}

	public Settings setTrainersMod(boolean... bools) {
		return setTrainersMod(getEnum(TrainersMod.class, bools));
	}

	public boolean isRivalCarriesStarterThroughout() {
		return rivalCarriesStarterThroughout;
	}

	public Settings setRivalCarriesStarterThroughout(
			boolean rivalCarriesStarterThroughout) {
		this.rivalCarriesStarterThroughout = rivalCarriesStarterThroughout;
		return this;
	}

	public boolean isTrainersUsePokemonOfSimilarStrength() {
		return trainersUsePokemonOfSimilarStrength;
	}

	public Settings setTrainersUsePokemonOfSimilarStrength(
			boolean trainersUsePokemonOfSimilarStrength) {
		this.trainersUsePokemonOfSimilarStrength = trainersUsePokemonOfSimilarStrength;
		return this;
	}

	public boolean isTrainersMatchTypingDistribution() {
		return trainersMatchTypingDistribution;
	}

	public Settings setTrainersMatchTypingDistribution(
			boolean trainersMatchTypingDistribution) {
		this.trainersMatchTypingDistribution = trainersMatchTypingDistribution;
		return this;
	}

	public boolean isTrainersBlockLegendaries() {
		return trainersBlockLegendaries;
	}

	public Settings setTrainersBlockLegendaries(boolean trainersBlockLegendaries) {
		this.trainersBlockLegendaries = trainersBlockLegendaries;
		return this;
	}

	public boolean isTrainersBlockEarlyWonderGuard() {
		return trainersBlockEarlyWonderGuard;
	}

	public Settings setTrainersBlockEarlyWonderGuard(
			boolean trainersBlockEarlyWonderGuard) {
		this.trainersBlockEarlyWonderGuard = trainersBlockEarlyWonderGuard;
		return this;
	}

	public boolean isRandomizeTrainerNames() {
		return randomizeTrainerNames;
	}

	public Settings setRandomizeTrainerNames(boolean randomizeTrainerNames) {
		this.randomizeTrainerNames = randomizeTrainerNames;
		return this;
	}

	public boolean isRandomizeTrainerClassNames() {
		return randomizeTrainerClassNames;
	}

	public Settings setRandomizeTrainerClassNames(
			boolean randomizeTrainerClassNames) {
		this.randomizeTrainerClassNames = randomizeTrainerClassNames;
		return this;
	}

	public WildPokemonMod getWildPokemonMod() {
		return wildPokemonMod;
	}

	public Settings setWildPokemonMod(WildPokemonMod wildPokemonMod) {
		this.wildPokemonMod = wildPokemonMod;
		return this;
	}

	public Settings setWildPokemonMod(boolean... bools) {
		return setWildPokemonMod(getEnum(WildPokemonMod.class, bools));
	}

	public WildPokemonRestrictionMod getWildPokemonRestrictionMod() {
		return wildPokemonRestrictionMod;
	}

	public Settings setWildPokemonRestrictionMod(
			WildPokemonRestrictionMod wildPokemonRestrictionMod) {
		this.wildPokemonRestrictionMod = wildPokemonRestrictionMod;
		return this;
	}

	public Settings setWildPokemonRestrictionMod(boolean... bools) {
		return setWildPokemonRestrictionMod(getEnum(
				WildPokemonRestrictionMod.class, bools));
	}

	public boolean isUseTimeBasedEncounters() {
		return useTimeBasedEncounters;
	}

	public Settings setUseTimeBasedEncounters(boolean useTimeBasedEncounters) {
		this.useTimeBasedEncounters = useTimeBasedEncounters;
		return this;
	}

	public boolean isBlockWildLegendaries() {
		return blockWildLegendaries;
	}

	public Settings setBlockWildLegendaries(boolean blockWildLegendaries) {
		this.blockWildLegendaries = blockWildLegendaries;
		return this;
	}

	public boolean isUseMinimumCatchRate() {
		return useMinimumCatchRate;
	}

	public Settings setUseMinimumCatchRate(boolean useMinimumCatchRate) {
		this.useMinimumCatchRate = useMinimumCatchRate;
		return this;
	}

	public boolean isRandomizeWildPokemonHeldItems() {
		return randomizeWildPokemonHeldItems;
	}

	public Settings setRandomizeWildPokemonHeldItems(
			boolean randomizeWildPokemonHeldItems) {
		this.randomizeWildPokemonHeldItems = randomizeWildPokemonHeldItems;
		return this;
	}

	public boolean isBanBadRandomWildPokemonHeldItems() {
		return banBadRandomWildPokemonHeldItems;
	}

	public Settings setBanBadRandomWildPokemonHeldItems(
			boolean banBadRandomWildPokemonHeldItems) {
		this.banBadRandomWildPokemonHeldItems = banBadRandomWildPokemonHeldItems;
		return this;
	}

	public StaticPokemonMod getStaticPokemonMod() {
		return staticPokemonMod;
	}

	public Settings setStaticPokemonMod(StaticPokemonMod staticPokemonMod) {
		this.staticPokemonMod = staticPokemonMod;
		return this;
	}

	public Settings setStaticPokemonMod(boolean... bools) {
		return setStaticPokemonMod(getEnum(StaticPokemonMod.class, bools));
	}

	public TMsMod getTmsMod() {
		return tmsMod;
	}

	public Settings setTmsMod(TMsMod tmsMod) {
		this.tmsMod = tmsMod;
		return this;
	}

	public Settings setTmsMod(boolean... bools) {
		return setTmsMod(getEnum(TMsMod.class, bools));
	}

	public boolean isTmLevelUpMoveSanity() {
		return tmLevelUpMoveSanity;
	}

	public Settings setTmLevelUpMoveSanity(boolean tmLevelUpMoveSanity) {
		this.tmLevelUpMoveSanity = tmLevelUpMoveSanity;
		return this;
	}

	public boolean isKeepFieldMoveTMs() {
		return keepFieldMoveTMs;
	}

	public Settings setKeepFieldMoveTMs(boolean keepFieldMoveTMs) {
		this.keepFieldMoveTMs = keepFieldMoveTMs;
		return this;
	}

	public TMsHMsCompatibilityMod getTmsHmsCompatibilityMod() {
		return tmsHmsCompatibilityMod;
	}

	public Settings setTmsHmsCompatibilityMod(
			TMsHMsCompatibilityMod tmsHmsCompatibilityMod) {
		this.tmsHmsCompatibilityMod = tmsHmsCompatibilityMod;
		return this;
	}

	public Settings setTmsHmsCompatibilityMod(boolean... bools) {
		return setTmsHmsCompatibilityMod(getEnum(TMsHMsCompatibilityMod.class,
				bools));
	}

	public MoveTutorMovesMod getMoveTutorMovesMod() {
		return moveTutorMovesMod;
	}

	public Settings setMoveTutorMovesMod(MoveTutorMovesMod moveTutorMovesMod) {
		this.moveTutorMovesMod = moveTutorMovesMod;
		return this;
	}

	public Settings setMoveTutorMovesMod(boolean... bools) {
		return setMoveTutorMovesMod(getEnum(MoveTutorMovesMod.class, bools));
	}

	public boolean isTutorLevelUpMoveSanity() {
		return tutorLevelUpMoveSanity;
	}

	public Settings setTutorLevelUpMoveSanity(boolean tutorLevelUpMoveSanity) {
		this.tutorLevelUpMoveSanity = tutorLevelUpMoveSanity;
		return this;
	}

	public boolean isKeepFieldMoveTutors() {
		return keepFieldMoveTutors;
	}

	public Settings setKeepFieldMoveTutors(boolean keepFieldMoveTutors) {
		this.keepFieldMoveTutors = keepFieldMoveTutors;
		return this;
	}

	public MoveTutorsCompatibilityMod getMoveTutorsCompatibilityMod() {
		return moveTutorsCompatibilityMod;
	}

	public Settings setMoveTutorsCompatibilityMod(
			MoveTutorsCompatibilityMod moveTutorsCompatibilityMod) {
		this.moveTutorsCompatibilityMod = moveTutorsCompatibilityMod;
		return this;
	}

	public Settings setMoveTutorsCompatibilityMod(boolean... bools) {
		return setMoveTutorsCompatibilityMod(getEnum(
				MoveTutorsCompatibilityMod.class, bools));
	}

	public InGameTradesMod getInGameTradesMod() {
		return inGameTradesMod;
	}

	public Settings setInGameTradesMod(InGameTradesMod inGameTradesMod) {
		this.inGameTradesMod = inGameTradesMod;
		return this;
	}

	public Settings setInGameTradesMod(boolean... bools) {
		return setInGameTradesMod(getEnum(InGameTradesMod.class, bools));
	}

	public boolean isRandomizeInGameTradesNicknames() {
		return randomizeInGameTradesNicknames;
	}

	public Settings setRandomizeInGameTradesNicknames(
			boolean randomizeInGameTradesNicknames) {
		this.randomizeInGameTradesNicknames = randomizeInGameTradesNicknames;
		return this;
	}

	public boolean isRandomizeInGameTradesOTs() {
		return randomizeInGameTradesOTs;
	}

	public Settings setRandomizeInGameTradesOTs(boolean randomizeInGameTradesOTs) {
		this.randomizeInGameTradesOTs = randomizeInGameTradesOTs;
		return this;
	}

	public boolean isRandomizeInGameTradesIVs() {
		return randomizeInGameTradesIVs;
	}

	public Settings setRandomizeInGameTradesIVs(boolean randomizeInGameTradesIVs) {
		this.randomizeInGameTradesIVs = randomizeInGameTradesIVs;
		return this;
	}

	public boolean isRandomizeInGameTradesItems() {
		return randomizeInGameTradesItems;
	}

	public Settings setRandomizeInGameTradesItems(
			boolean randomizeInGameTradesItems) {
		this.randomizeInGameTradesItems = randomizeInGameTradesItems;
		return this;
	}

	public FieldItemsMod getFieldItemsMod() {
		return fieldItemsMod;
	}

	public Settings setFieldItemsMod(FieldItemsMod fieldItemsMod) {
		this.fieldItemsMod = fieldItemsMod;
		return this;
	}

	public Settings setFieldItemsMod(boolean... bools) {
		return setFieldItemsMod(getEnum(FieldItemsMod.class, bools));
	}

	public boolean isBanBadRandomFieldItems() {
		return banBadRandomFieldItems;
	}

	public Settings setBanBadRandomFieldItems(boolean banBadRandomFieldItems) {
		this.banBadRandomFieldItems = banBadRandomFieldItems;
		return this;
	}

	private static int makeByteSelected(boolean... bools) {
		if (bools.length > 8) {
			throw new IllegalArgumentException(
					"Can't set more than 8 bits in a byte!");
		}

		int initial = 0;
		int state = 1;
		for (boolean b : bools) {
			initial |= b ? state : 0;
			state *= 2;
		}
		return initial;
	}

	private static boolean restoreState(byte b, int index) {
		if (index >= 8) {
			throw new IllegalArgumentException(
					"Can't read more than 8 bits from a byte!");
		}

		int value = b & 0xFF;
		return ((value >> index) & 0x01) == 0x01;
	}

	private static void writeFullInt(ByteArrayOutputStream out, int value)
			throws IOException {
		byte[] crc = ByteBuffer.allocate(4).putInt(value).array();
		out.write(crc);
	}

	private static void write2ByteInt(ByteArrayOutputStream out, int value) {
		out.write(value & 0xFF);
		out.write((value >> 8) & 0xFF);
	}

	public static <E extends Enum<E>> E restoreEnum(Class<E> clazz, byte b,
			int... indices) {
		boolean[] bools = new boolean[indices.length];
		int i = 0;
		for (int idx : indices) {
			bools[i] = restoreState(b, idx);
			i++;
		}
		return getEnum(clazz, bools);
	}

	@SuppressWarnings("unchecked")
	public static <E extends Enum<E>> E getEnum(Class<E> clazz,
			boolean... bools) {
		int index = getSetEnum(clazz.getSimpleName(), bools);
		try {
			return ((E[]) clazz.getMethod("values").invoke(null))[index];
		} catch (Exception e) {
			throw new IllegalArgumentException(String.format(
					"Unable to parse enum of type %s", clazz.getSimpleName()),
					e);
		}
	}

	private static int getSetEnum(String type, boolean... bools) {
		int index = -1;
		for (int i = 0; i < bools.length; i++) {
			if (bools[i]) {
				if (index >= 0) {
					throw new IllegalStateException(String.format(
							"Only one value for %s may be chosen!", type));
				}
				index = i;
			}
		}
		// We have to return something, so return the default
		return index >= 0 ? index : 0;
	}

	private static void checkChecksum(byte[] data) {
		// Check the checksum
		ByteBuffer buf = ByteBuffer.allocate(4).put(data, data.length - 16, 4);
		buf.rewind();
		int crc = buf.getInt();

		CRC32 checksum = new CRC32();
		checksum.update(data, 0, data.length - 16);

		if ((int) checksum.getValue() != crc) {
			throw new IllegalArgumentException("Malformed input string");
		}
	}

}
