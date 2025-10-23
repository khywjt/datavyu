/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.datavyu;

import ch.randelshofer.quaqua.QuaquaManager;
import com.brsanthu.googleanalytics.GoogleAnalytics;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.datavyu.controllers.project.ProjectController;
import org.datavyu.models.db.TitleNotifier;
import org.datavyu.models.db.UserWarningException;
import org.datavyu.plugins.PluginManager;
import org.datavyu.plugins.StreamViewer;
import org.datavyu.undoableedits.SpreadsheetUndoManager;
import org.datavyu.util.*;
import org.datavyu.views.*;
import org.datavyu.views.discrete.SpreadSheetPanel;
import org.jdesktop.application.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.EventObject;

/**
 * The main class for Datavyu
 */
public final class Datavyu extends SingleFrameApplication implements KeyEventDispatcher, TitleNotifier {

    /**
     * Supported platforms for Datavyu
     */
    public enum Platform {
        MAC, // Generic Mac platform. I.e. Tiger, Leopard, Snow Leopard
        WINDOWS, // Generic openWindows platform. I.e. XP, vista, etc.
        LINUX, // Generic Linux platform.
        UNKNOWN
    }

    /** Logger for this class */
    private static Logger logger = LogManager.getLogger(Datavyu.class);

    /** The desired minimum initial width */
    private static final int INIT_MIN_X = 600;

    /** The desired minimum initial height */
    private static final int INIT_MIN_Y = 700;

    // TODO: Move this to some OS specifics
    private static boolean osxPressAndHoldEnabled;

    /**
     * Constant variable for the Datavyu main panel to send keyboard shortcuts while the QTController is in focus.
     * This is initialized in the 'startup' method.
     */
    private static DatavyuView datavyuView;

    /** The video controller */
    private static VideoController videoController;

    /** The project controller */
    private static ProjectController projectController;

    private static GoogleAnalytics ga;

    // Load native libraries
    static {
        logger.info("Working directory is: " + System.getProperty("user.dir"));
        logger.info("Class path is: " + System.getProperty("java.class.path"));
        logger.info("The java library path is: " + System.getProperty("java.library.path"));

        switch (getPlatform()) {
            case MAC:
                logger.info("Detected platform: MAC");
                // Load library for look and feel under Mac OSX
                try {
                    NativeLibraryLoader.extractAndLoad("quaqua64");
                } catch (Exception e) {
                    logger.error("Could not load library quaqua64 for mac OS " + e);
                }
                if (MacOS.isOSXPressAndHoldEnabled()) {
                    osxPressAndHoldEnabled = true;
                    MacOS.setOSXPressAndHoldValue(false);
                }
                break;

            case WINDOWS:
                // nothing to do here
                break;

            default:
                logger.error("Unknown or unsupported platform!");
                break;
        }
    }

    /** Tracks if a NumPad key has been pressed */
    private boolean numKeyDown = false;

    /** This application is ready to open a file */
    public boolean readyToOpenFile = false;

    /** File path from the command line */
    private String commandLineFile;


    /**
     * @return The platform that Datavyu is running on.
     */
    public static Platform getPlatform() {
        String os = System.getProperty("os.name");
        if (os.contains("Mac")) {
            return Platform.MAC;
        }
        if (os.contains("Win")) {
            return Platform.WINDOWS;
        }
        if (os.contains("Linux")) {
            return Platform.LINUX;
        }
        return Platform.UNKNOWN;
    }

    /**
     * Get the single instance of the video controller used in Datavyu
     *
     * @return Video controller
     */
    public static VideoController getVideoController() {
        return videoController;
    }

    /**
     * Sets the video controller
     *
     * @param videoController The video controller
     */
    public static void setVideoController(VideoController videoController) {
        Datavyu.videoController = videoController;
    }

    /**
     * Gets application instance
     *
     * @return Instance of the Datavyu application
     */
    public static Datavyu getApplication() {
        return Application.getInstance(Datavyu.class);
    }

    /**
     * Main method launching the application
     *
     * @param args The command line arguments passed to Datavyu.
     */
    public static void main(final String[] args) {
        // ★ GEMINI: START OF MODIFICATION - 注册 CJK 字体
        // 在任何 UI 创建之前调用字体注册方法，以确保 Swing 组件（如电子表格）能显示中文
        installCJKUIFont();
        // ★ GEMINI: END OF MODIFICATION

        // If we are running on a MAC set system properties
        if (Datavyu.getPlatform() == Platform.MAC) {
            System.setProperty("Quaqua.jniIsPreloaded", "true");
        }
        launch(Datavyu.class, args);
    }

    // ★ GEMINI: START OF MODIFICATION - 添加 CJK 字体安装方法
    // 注册并统一设置支持中文的字体
    private static void installCJKUIFont() {
        try {
            // 加载内嵌在 resources/fonts 下的字体（你已经把 DejaVuSansCondensed.ttf 换成中文字体）
            java.io.InputStream fontStream = Datavyu.class.getResourceAsStream("/fonts/DejaVuSansCondensed.ttf");
            java.awt.Font base = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, fontStream);
            java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(base);
            java.awt.Font ui = base.deriveFont(java.awt.Font.PLAIN, 13f);

            // 覆盖常用 Swing 组件的默认字体
            javax.swing.UIManager.put("Label.font",          new javax.swing.plaf.FontUIResource(ui));
            javax.swing.UIManager.put("TextField.font",      new javax.swing.plaf.FontUIResource(ui));
            javax.swing.UIManager.put("TextArea.font",       new javax.swing.plaf.FontUIResource(ui));
            javax.swing.UIManager.put("EditorPane.font",     new javax.swing.plaf.FontUIResource(ui));
            javax.swing.UIManager.put("FormattedTextField.font", new javax.swing.plaf.FontUIResource(ui));
            javax.swing.UIManager.put("Table.font",          new javax.swing.plaf.FontUIResource(ui));
            javax.swing.UIManager.put("TableHeader.font",    new javax.swing.plaf.FontUIResource(ui));
            javax.swing.UIManager.put("List.font",           new javax.swing.plaf.FontUIResource(ui));
            javax.swing.UIManager.put("Menu.font",           new javax.swing.plaf.FontUIResource(ui));
            javax.swing.UIManager.put("MenuItem.font",       new javax.swing.plaf.FontUIResource(ui));
            javax.swing.UIManager.put("CheckBoxMenuItem.font", new javax.swing.plaf.FontUIResource(ui));
            javax.swing.UIManager.put("Button.font",         new javax.swing.plaf.FontUIResource(ui));
            javax.swing.UIManager.put("ToggleButton.font",   new javax.swing.plaf.FontUIResource(ui));
            javax.swing.UIManager.put("ToolTip.font",        new javax.swing.plaf.FontUIResource(ui));
            javax.swing.UIManager.put("TitledBorder.font",   new javax.swing.plaf.FontUIResource(ui));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    // ★ GEMINI: END OF MODIFICATION

    // TODO: Why do we have these two project controllers?
    public static ProjectController getProjectController() {
        if (datavyuView != null && datavyuView.getSpreadsheetPanel() != null
                && datavyuView.getSpreadsheetPanel().getProjectController() != null) {
            return datavyuView.getSpreadsheetPanel().getProjectController();
        }
        return projectController;
    }

    public static GoogleAnalytics getGoogleAnalytics() {
        return ga;
    }

    public static void setProjectController(ProjectController p) {
        projectController = p;
    }

    public static DatavyuView getView() {
        return datavyuView;
    }

    public void setCommandLineFile(String commandLineFile) {
        logger.info("Command line file set to " + commandLineFile);
        this.commandLineFile = commandLineFile;
    }

    /**
     * Dispatches the keystroke to the correct action.
     *
     * @param evt The event that triggered this action.
     * @return true if the KeyboardFocusManager should take no further action
     * with regard to the KeyEvent; false otherwise
     */
    @Override
    public boolean dispatchKeyEvent(final KeyEvent evt) {

        /**
         * This switch is for hot keys that are on the main section of the
         * keyboard.
         */
        int modifiers = evt.getModifiers();

        // BugzID:468 - Define accelerator keys based on OS.
        int keyMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

        // If we are typing a key that is a shortcut - we consume it immediately.
        if ((evt.getID() == KeyEvent.KEY_TYPED) && (modifiers == keyMask)) {

            // datavyuView also has the fun of key accelerator handling. If it is
            // focused, let it handle the fun or everything is done twice. If it
            // doesn't have focus we manually handle it in the switch below.
            if (getView().getFrame().isFocused()) {
                evt.consume();
                return true;
            }

            switch (getPlatform()) {

                // Code table used by Windows is different.
                case WINDOWS: {
                    switch (WindowsOS.remapKeyChar(evt.getKeyChar())) {
                        case '+':
                        case '-':
                            // Plus and minus do not respond. Uncomment
                            // the printout above to see what I mean.

                        case 'O':
                            getView().open();
                            evt.consume();

                            return true;

                        case 'S':
                            getView().save();
                            evt.consume();

                            return true;

                        case 'N':
                            getView().showNewProjectForm();
                            evt.consume();

                            return true;

                        case 'L':
                            getView().newCellLeft();
                            evt.consume();

                            return true;

                        case 'R':
                            getView().newCellRight();
                            evt.consume();

                            return true;

                        case 'W':
                            if(safeQuit(getView().getTabbedPane().getSelectedComponent())) {
                                getView().getTabbedPane().remove(getView().getTabbedPane().getSelectedComponent());
                            }
                            if(getView().getTabbedPane().getTabCount() == 0) {
                                Datavyu.getApplication().exit();
                                System.exit(0);
                            }
                            evt.consume();
                            return true;

                        default:
                            break;
                    }
                }

                break;

                default: {

                    switch (evt.getKeyChar()) {

                        case '=': // Can't access + without shift.
                            getView().zoomIn();
                            evt.consume();

                            return true;

                        case '-':
                            getView().zoomOut();
                            evt.consume();

                            return true;

                        case 'o':
                            getView().open();
                            evt.consume();

                            return true;

                        case 's':
                            getView().save();
                            evt.consume();

                            return true;

                        case 'n':
                            getView().showNewProjectForm();
                            evt.consume();

                            return true;

                        case 'l':
                            getView().newCellLeft();
                            evt.consume();

                            return true;

                        case 'r':
                            getView().newCellRight();
                            evt.consume();

                            return true;

                        case 'w':
                            safeQuit(getView().getTabbedPane().getSelectedComponent());
                            evt.consume();
                            return true;

                        default:
                            break;
                    }
                }
            }
        }


        if ((evt.getID() == KeyEvent.KEY_PRESSED) && (evt.getKeyLocation() == KeyEvent.KEY_LOCATION_STANDARD)) {

            switch (evt.getKeyCode()) {
                /**
                 * This case is because VK_PLUS is not linked to a key on the
                 * English keyboard. So the GUI is bound to VK_PLUS and VK_SUBTACT.
                 * VK_SUBTRACT is on the numpad, but this is short-circuited above.
                 * The cases return true to let the KeyboardManager know that there
                 * is nothing left to be done with these keys.
                 */
                case KeyEvent.VK_EQUALS:
                    if (modifiers == keyMask) {
                        datavyuView.changeFontSize(DatavyuView.ZOOM_INTERVAL);
                    }
                    return true;

                case KeyEvent.VK_MINUS:
                    if (modifiers == keyMask) {
                        datavyuView.changeFontSize(-DatavyuView.ZOOM_INTERVAL);
                    }
                    return true;

                default:
                    break;
            }

            //Bypass the spreadsheet menu bar accelerator to handle new left and right cell shortcuts
            if (modifiers == keyMask){

                switch (getPlatform()){
                    case WINDOWS:
                        switch (WindowsOS.remapKeyChar(evt.getKeyChar())){
                            case 'L':
                                getView().newCellLeft();
                                evt.consume();

                                return true;

                            case 'R':
                                getView().newCellRight();
                                evt.consume();

                                return true;

                            default:
                                break;
                        }

                        break;

                    default: {

                        switch (evt.getKeyChar()) {
                            case 'l':
                                getView().newCellLeft();
                                evt.consume();

                                return true;

                            case 'r':
                                getView().newCellRight();
                                evt.consume();

                                return true;

                            default:
                                break;
                        }
                    }
                }
            }
        }

        // BugzID:784 - Shift key is passed to Data Controller.
        if (evt.getKeyCode() == KeyEvent.VK_SHIFT) {
            if (evt.getID() == KeyEvent.KEY_PRESSED) {
                videoController.setShiftMask(true);
            } else {
                videoController.setShiftMask(false);
            }
        }

        /**
         * The following cases handle numpad keystrokes.
         */
        if ((evt.getID() == KeyEvent.KEY_PRESSED) && (evt.getKeyLocation() == KeyEvent.KEY_LOCATION_NUMPAD)) {
            numKeyDown = true;
        } else if (numKeyDown && (evt.getID() == KeyEvent.KEY_TYPED)) {
            return true;
        }

        if ((evt.getID() == KeyEvent.KEY_RELEASED) && (evt.getKeyLocation() == KeyEvent.KEY_LOCATION_NUMPAD)) {
            numKeyDown = false;
        }

        if (!numKeyDown) {
            return false;
        }

        boolean result = true;

        switch (evt.getKeyCode()) {

            case KeyEvent.VK_DIVIDE:
                //point cell
                videoController.pressPointCell();
                break;

            case KeyEvent.VK_ASTERISK:

                break;
            case KeyEvent.VK_MULTIPLY:
                //Show/hide
                videoController.pressShowTracksSmall();
                break;

            case KeyEvent.VK_NUMPAD7:
                videoController.pressSetCellOnset();

                break;

            case KeyEvent.VK_NUMPAD8:
                videoController.pressPlay();

                break;

            case KeyEvent.VK_NUMPAD9:
                videoController.pressSetCellOffsetNine();

                break;

            case KeyEvent.VK_NUMPAD4:
                videoController.pressShuttleBack();

                break;

            case KeyEvent.VK_NUMPAD5:
                videoController.pressStop();

                break;

            case KeyEvent.VK_NUMPAD6:
                videoController.pressShuttleForward();

                break;

            case KeyEvent.VK_NUMPAD1:

                // We don't do the press Jog thing for jogging - as users often
                // just hold the button down... Which causes weird problems when
                // attempting to do multiple presses.
                videoController.jogBackAction();

                break;

            case KeyEvent.VK_NUMPAD2:
                videoController.pressPause();

                break;

            case KeyEvent.VK_NUMPAD3:

                // We don't do the press Jog thing for jogging - as users often
                // just hold the button down... Which causes weird problems when
                // attempting to do multiple presses.
                videoController.jogForwardAction();

                break;

            case KeyEvent.VK_NUMPAD0:
                videoController.pressCreateNewCellSettingOffset();

                break;

            case KeyEvent.VK_DECIMAL:
                videoController.pressSetCellOffsetPeriod();

                break;

            case KeyEvent.VK_SUBTRACT:

                if (modifiers == InputEvent.CTRL_MASK) {
                    videoController.clearRegionOfInterestAction();
                } else if (modifiers == InputEvent.SHIFT_MASK){
                    videoController.pressJumpForward();
                } else {
                    videoController.pressJumpBack();
                }

                break;

            case KeyEvent.VK_ADD:

                if (modifiers == InputEvent.SHIFT_MASK) {
                    videoController.findOffsetAction();
                    videoController.pressFind();
                } else if (modifiers == InputEvent.CTRL_MASK) {
                    // IMPORTANT: Don't change the order of
                    // the video controller calls, it will break
                    // the features.
                    videoController.setRegionOfInterestAction();
                    videoController.pressFind();
                } else {
                    videoController.pressFind();
                }

                break;

            case KeyEvent.VK_ENTER:
                videoController.pressCreateNewCell();

                break;

            default:

                // Do nothing with the key.
                result = false;

                break;
        }

        return result;
    }

    /**
     * Action for showing the quicktime video controller.
     */
    public void showVideoController() {
        Datavyu.getApplication().show(videoController);
        videoController.setShouldBeVisible(true);
    }

    /**
     * Action for showing the variable list.
     */
    public void showVariableList() {
        JFrame mainFrame = Datavyu.getView().getFrame();
        VariableListV listVarView = new VariableListV(mainFrame, false, projectController.getDataStore());
        listVarView.registerListeners();
        Datavyu.getApplication().show(listVarView);
    }

    /**
     * Action for showing the Undo History.
     */
    public void showHistory() {
        JFrame mainFrame = Datavyu.getView().getFrame();
        SpreadsheetUndoManager undoManager = getView().getSpreadsheetUndoManager();
        UndoHistoryWindow history = new UndoHistoryWindow(mainFrame, false, undoManager);
        Datavyu.getApplication().show(history);
    }

    /**
     * Action for showing the about window.
     */
    public void showAboutWindow() {
        JFrame mainFrame = Datavyu.getView().getFrame();
        AboutV aboutWindow = new AboutV(mainFrame, false);
        Datavyu.getApplication().show(aboutWindow);
    }

    /**
     * Action for opening the support site
     */
    public void openSupportSite() {
        String url = ConfigProperties.getInstance().getSupportSiteUrl();
        try {
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
        } catch (java.io.IOException e) {
            logger.error("Error while opening support site " + e);
        }
    }

    /**
     * Action for opening the guide site
     */
    public void openGuideSite() {
        String url = ConfigProperties.getInstance().getUserGuideUrl();
        try {
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
        } catch (java.io.IOException e) {
            logger.error("Error while opening guide site " + e);
        }
    }

    /**
     * Action for showing the about window.
     */
    public void showUpdateWindow() {
        Datavyu.getApplication().show(new UpdateVersion(Datavyu.getView().getFrame(), true));
    }

    /**
     * Show a warning dialog to the user.
     *
     * @param s The message to present to the user.
     */
    public void showWarningDialog(final String s) {
        JFrame mainFrame = Datavyu.getView().getFrame();
        ResourceMap resourceMap = Application.getInstance(Datavyu.class).getContext().getResourceMap(Datavyu.class);
        JOptionPane.showMessageDialog(mainFrame, s, resourceMap.getString("WarningDialog.title"),
                JOptionPane.WARNING_MESSAGE);
    }

    /**
     * Show a warning dialog to the user.
     *
     * @param e The UserWarningException to present to the user.
     */
    public void showWarningDialog(final UserWarningException e) {
        showWarningDialog(e.getMessage());
    }

    /**
     * User quits- check for save needed. Note that this can be used even in
     * situations when the application is not truly "quitting", but just the
     * database information is being lost (e.g. on an "open" or "new"
     * instruction). In all interpretations, "true" indicates that all unsaved
     * changes are to be discarded.
     *
     * @return True for quit, false otherwise.
     */
    public boolean safeQuit() {
        JFrame mainFrame = Datavyu.getApplication().getMainFrame();
        ResourceMap resourceMap = Application.getInstance(Datavyu.class).getContext().getResourceMap(Datavyu.class);
        if (getView().checkAllTabsForChanges()) {
            for (Component tab : getView().getTabbedPane().getComponents()) {
                if (tab instanceof SpreadSheetPanel) {
                    SpreadSheetPanel sp = (SpreadSheetPanel) tab;
                    getView().getTabbedPane().setSelectedComponent(sp);
                    // Ask to save if this spreadsheet has been changed
                    if (sp.getProjectController().isChanged()) {
                        String cancelOption = "Cancel";
                        String noOption = "Don't save";
                        String yesOption = "Save";

                        String[] options = getPlatform() == Platform.MAC ? MacOS.getOptions(yesOption, noOption, cancelOption) :
                                WindowsOS.getOptions(yesOption, noOption, cancelOption);

                        // Get name of spreadsheet.  Check in both project and datastore.
                        String projectName = sp.getProjectController().getProjectName();

                        if (projectName == null) {
                            projectName = sp.getDataStore().getName();
                        }

                        int selectedOption = JOptionPane.showOptionDialog(mainFrame,
                                resourceMap.getString("UnsavedDialog.message",projectName),
                                resourceMap.getString("UnDavedDialog.title"),
                                JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
                                null, options, yesOption);

                        if (selectedOption == 0) {
                            getView().save();
                        }

                        return getPlatform() == Platform.MAC ? !(selectedOption == 1) : !(selectedOption == 2);
                    }
                }
            }
            // User has been asked whether or not to save each file, we can return now
            return true;
        } else {
            // Project hasn't been changed.
            return true;
        }
    }

    /**
     * Function to check whether or not it is OK to close this tab
     */
    public boolean safeQuit(Component tab) {
        JFrame mainFrame = Datavyu.getApplication().getMainFrame();
        ResourceMap resourceMap = Application.getInstance(Datavyu.class).getContext().getResourceMap(Datavyu.class);
        SpreadSheetPanel spreadsheetPanel = (SpreadSheetPanel) tab;
        if (spreadsheetPanel.getProjectController().isChanged()) {
            getView().getTabbedPane().setSelectedComponent(spreadsheetPanel);

            String cancelOption = "Cancel";
            String noOption = "Don't save";
            String yesOption = "Save";

            String[] options = getPlatform() == Platform.MAC ? MacOS.getOptions(yesOption, noOption, cancelOption) :
                    WindowsOS.getOptions(yesOption, noOption, cancelOption);

            // Get project name.
            String projName = spreadsheetPanel.getProjectController().getProjectName();
            if (projName == null) {
                projName = spreadsheetPanel.getDataStore().getName();
            }
            
            int selectedOption = JOptionPane.showOptionDialog(mainFrame,
                    resourceMap.getString("UnsavedDialog.tabmessage", projName),
                    resourceMap.getString("UnsavedDialog.title"),
                    JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, options, yesOption);

            if (selectedOption == 0) {
                getView().save();
            }

            // Mac: yes | cancel | no   and Windows: yes | no | cancel
            return getPlatform() == Platform.MAC ? !(selectedOption == 1) : !(selectedOption == 2);
        } else {
            return true;
        }
    }

    /**
     * If the user is trying to save over an existing file, prompt them whether
     * they they wish to continue.
     *
     * @return True for overwrite, false otherwise.
     */
    public boolean overwriteExisting() {
        JFrame mainFrame = Datavyu.getApplication().getMainFrame();
        ResourceMap resourceMap = Application.getInstance(Datavyu.class).getContext().getResourceMap(Datavyu.class);
        String defaultOption = "Cancel";
        String alternativeOption = "Overwrite";
        String[] options = getPlatform() == Platform.MAC ? MacOS.getOptions(defaultOption, alternativeOption) :
                WindowsOS.getOptions(defaultOption, alternativeOption);

        int selectedOption = JOptionPane.showOptionDialog(
                mainFrame,
                resourceMap.getString("OverwriteDialog.message"),
                resourceMap.getString("OverwriteDialog.title"),
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                defaultOption);

        return getPlatform() == Platform.MAC ? selectedOption == 1 : selectedOption == 0;
    }

    @Override
    protected void initialize(final String[] args) {

        switch (getPlatform()) {
            case MAC:
                try {
                    UIManager.setLookAndFeel(QuaquaManager.getLookAndFeel());
                } catch (UnsupportedLookAndFeelException e) {
                    logger.error("Failed to set Quaqua LNF " + e);
                }
                MacOS.loadCompileHandler();
                break;
            case WINDOWS:
                // BugzID:1288
                try {
                    WindowsOS.associate(".opf", WindowsOS.cwd().toString());
                } catch (Exception e) {
                    logger.error("Could not associate .opf " + e.getMessage());
                }
                break;
        }

        // This is for handling files opened from the command line.
        if (args.length > 0) {
            commandLineFile = args[0];
        }

        try {
            // Check for updates
            if (DatavyuVersion.isUpdateAvailable() && !DatavyuVersion.isIgnoreVersion()) {
                Datavyu.getApplication().show(new UpdateVersion(Datavyu.getApplication().getMainFrame(), true));
            }
        } catch (ExceptionInInitializerError e) {
            logger.error("Cannot reach Datavyu servers " + e.getMessage());
        }
    }

    /**
     * At startup create and show the main frame of the application.
     */
    @Override
    protected void startup() {

        // Make view the new view so we can keep track of it for hot keys
        datavyuView = new DatavyuView(this);
        show(datavyuView);
        datavyuView.getFileSplitPane().setDividerLocation(0.75);

        // BugzID:435 - Correct size if a small size is detected
        int width = (int) getMainFrame().getSize().getWidth();
        int height = (int) getMainFrame().getSize().getHeight();

        if ((width < INIT_MIN_X) || (height < INIT_MIN_Y)) {
            int x = Math.max(width, INIT_MIN_X);
            int y = Math.max(height, INIT_MIN_Y);
            getMainFrame().setSize(x, y);
        }

        addExitListener(new ExitListener() {
            @Override
            public boolean canExit(EventObject eventObject) {
                return safeQuit();
            }

            @Override
            public void willExit(EventObject eventObject) { /* nothing to do here */ }
        });

        // Create video controller.
        projectController = new ProjectController();
        videoController = datavyuView.getSpreadsheetPanel().getVideoController();

        final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int x = getView().getFrame().getX();

        // don't let the data viewer fall below the bottom of the primary
        // screen, but also don't let it creep up above the screen either
        int y = getView().getFrame().getY() + getView().getFrame().getHeight();
        y = (int) Math.max(Math.min(y, screenSize.getHeight() - videoController.getHeight()), 0);
        videoController.setLocation(x, y);
        show(videoController);
        datavyuView.checkForAutosavedFile();
        datavyuView.checkFirstStart();

        // The DB we create by default doesn't really have any unsaved changes.
        projectController.getDataStore().markAsUnchanged();
        ready();

        if (ConfigProperties.getInstance().getShareData()) {

            ga = GoogleAnalytics.builder()
                .withTrackingId("UA-129130876-3")
                .build();

            ResourceMap resourceMap = Application.getInstance(Datavyu.class).getContext()
                .getResourceMap(Build.class);

            logger.debug("Application Name: Datavyu"
                + " ,Application Version: " + resourceMap.getString("Application.version")
                + " ,OS Name: "Dystem.getProperty("os.name")
                + " ,OS Version: " + System.getProperty("os.version")
                + " ,Java Version: " + System.getProperty("java.version"));

            ga.screenView()
                .applicationName("datavyu")
                .applicationVersion(resourceMap.getString("Application.version"))
                .send();

            ga.event()
                .eventCategory("os")
                .eventAction("name")
                .eventLabel(System.getProperty("os.name"))
                .send();

            ga.event()
                .eventCategory("os")
                .eventAction("version")
                .eventLabel(System.getProperty("os.version"))
                .send();

            ga.event()
                .eventCategory("java")
                .eventAction("version")
                .eventLabel(System.getProperty("java.version"))
                .send();
        }
    }

    @Override
    protected void ready() {
        readyToOpenFile = true;
        if (commandLineFile != null) {
            getView().openExternalFile(new File(commandLineFile));
            commandLineFile = null;
        }
    }

    /**
     * Clean up after ourselves.
     */
    @Override
    public void shutdown() {
        if (getPlatform() == Platform.MAC && osxPressAndHoldEnabled) {
            MacOS.setOSXPressAndHoldValue(true);
        }

        if (ConfigProperties.getInstance().getShareData()) {
            for (StreamViewer streamViewer :getVideoController().getStreamViewers()) {
                String videoFormat = FilenameUtils.getExtension(streamViewer.getSourceFile().getName());
                String pluginName = PluginManager.getInstance()
                    .getAssociatedPlugin(streamViewer.getClass().getName()).getClass().getSimpleName();
                logger.debug("GA - Event - Plugin: " + pluginName
                                + " ,Video Format: " + videoFormat);
                ga.event()
                    .eventCategory("video")
                    .eventAction("format")
                    .eventLabel(videoFormat)
                    .send();

                ga.event()
                    .eventCategory("video")
                    .eventAction("plugin")
                    .eventLabel(pluginName)
                    .send();
            }
        }
        
        
        logger.info("Saving configuration properties.");
        ConfigProperties.save();
        super.shutdown();
    }

    @Override
    public void updateTitle() {
        // Update the main title
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (datavyuView != null) {
                    datavyuView.updateTitle();
                }
            }
        });
    }
}
