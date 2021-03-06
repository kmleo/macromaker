package fwcd.macromaker.ui;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import fwcd.macromaker.model.Macro;
import fwcd.macromaker.model.MacroSerializer;
import fwcd.macromaker.model.RobotProxy;
import fwcd.macromaker.model.action.MacroAction;
import fwcd.macromaker.model.shortcuts.KeyboardShortcutsModel;
import fwcd.macromaker.ui.dispatch.GlobalEventDispatcher;

public class MacroMakerViewController implements MacroMakerResponder, AutoCloseable {
	private final MacroMakerView view;
	
	private GlobalEventDispatcher eventDispatcher = new GlobalEventDispatcher();
	private RobotProxy robot = new AwtRobotProxy();
	private MacroRecorder recorder = new MacroRecorder();
	private MacroSerializer serializer = new MacroSerializer();
	private Macro macro = null;
	private Thread macroRunner = null;
	private boolean macroRunning = false;
	
	public MacroMakerViewController(KeyboardShortcutsModel shortcuts) {
		view = new MacroMakerView(this);
		eventDispatcher.registerListeners();
		
		ShortcutsHandler shortcutHandler = new ShortcutsHandler(shortcuts);
		shortcutHandler.addAction(CommonShortcuts.START_RECORDING, () -> record());
		shortcutHandler.addAction(CommonShortcuts.STOP, () -> stop());
		shortcutHandler.addAction(CommonShortcuts.PLAY, () -> play());
		eventDispatcher.addKeyListener(shortcutHandler);
	}
	
	@Override
	public void record() {
		recorder.reset();
		recorder.startRecording(eventDispatcher);
		view.setStatus("Recording...");
	}
	
	@Override
	public void stop() {
		if (macroRunning) {
			macroRunning = false;
		} else if (recorder != null) {
			recorder.stopRecording(eventDispatcher);
			macro = recorder.getRecordedMacro();
			view.setStatus("Idling...");
		}
	}
	
	@Override
	public void play() {
		if (macro == null) {
			view.showMessage("No macro recorded.");
		} else {
			macroRunning = true;
			view.setStatus("Playing...");
			final int repeats = view.getRepeats();
			
			macroRunner = new Thread(() -> {
				int i = 0;
				while (i < repeats && macroRunning) {
					try {
						runMacroWithProgress();
						i++;
					} catch (InterruptedException e) {
						break;
					}
				}
				view.setStatus("Idling...");
				macroRunning = false;
			});
			macroRunner.start();
		}
	}
	
	private void runMacroWithProgress() throws InterruptedException {
		if (macro == null) {
			throw new IllegalStateException("Tried to run non-present macro");
		}
		
		double elapsedPercent = 0;
		double duration = macro.getDurationMs();
		long elapsed = 0;
		
		for (MacroAction action : macro.getActions()) {
			long timeStamp = action.getTimeStamp();
			Thread.sleep(timeStamp - elapsed);
			
			action.run(robot);
			
			elapsed = timeStamp;
			elapsedPercent = elapsed / duration;
			view.updateProgress(elapsedPercent);
		}
	}
	
	@Override
	public void newMacro() {
		macro = null;
	}
	
	@Override
	public void open(Path path) {
		try (BufferedReader reader = Files.newBufferedReader(path)) {
			macro = serializer.deserialize(reader);
		} catch (IOException e) {
			view.showMessage("Could not open file: " + e.getMessage());
		}
	}
	
	@Override
	public void save(Path path) {
		if (macro == null) {
			view.showMessage("No macro recorded.");
		} else {
			try (BufferedWriter writer = Files.newBufferedWriter(path)) {
				serializer.serialize(macro, writer);
			} catch (IOException e) {
				view.showMessage("Could not save to file: " + e.getMessage());
			}
		}
	}
	
	public MacroMakerView getView() {
		return view;
	}
	
	@Override
	public void close() {
		eventDispatcher.close();
	}
}
