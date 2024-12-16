package com.github.zabbum.oelremakeclient;

import com.github.zabbum.oelremakeclient.artloader.ArtObject;
import com.github.zabbum.oelrlib.Player;
import com.github.zabbum.oelrlib.game.BaseGame;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.SimpleTheme;
import com.googlecode.lanterna.graphics.Theme;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.Component;
import com.googlecode.lanterna.gui2.GridLayout;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.gui2.table.Table;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import com.googlecode.lanterna.terminal.swing.SwingTerminalFontConfiguration;
import com.googlecode.lanterna.terminal.swing.SwingTerminalFrame;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Getter
public class Game {
    private static final String WS_ENDPOINT_URL = "ws://localhost:8080/gameplay";
    private static final String HTTP_ENDPOINT_URL = "http://localhost:8080/baseGame";
    private static final float FONT_SIZE = 15.0f;
    private static final int TERMINAL_WIDTH = 60;
    private static final int TERMINAL_HEIGHT = 34;

    private Map<String, String> argsMap;
    private GameProperties gameProperties;
    private Font font;
    private Terminal terminal;
    private Screen screen;
    private Window window;
    private Panel contentPanel;
    private RequestsCreator requestsCreator;

    public Game(String[] args) throws IOException, FontFormatException {
        this.argsMap = CliArgumentsParser.parseArguments(args);

        setUp();
    }

    private void setUp() throws IOException, FontFormatException {
        // Create gameProperties
        gameProperties = new GameProperties();

        setLanguage();
        setFont();
        configTerminal();
        createGUI();

        requestsCreator = new RequestsCreator(WS_ENDPOINT_URL, HTTP_ENDPOINT_URL);
    }

    private void setLanguage() throws IOException {
        String langCode;
        if (argsMap.get("lang") != null) {
            langCode = argsMap.get("lang");
        } else {
            langCode = "pl-PL";
        }

        InputStream inputStream = Application.class.getClassLoader()
                .getResourceAsStream("lang/" + langCode + ".json");
        gameProperties.setLangMap(LangExtractor.getLangData(inputStream));
    }

    private void setFont() throws IOException, FontFormatException {
        InputStream inputStream = Application.class.getClassLoader()
                .getResourceAsStream("font/C64_Pro_Mono-STYLE.ttf");

        if (inputStream == null) {
            throw new RuntimeException("Resource stream of font is null.");
        }

        Font fontTmp = Font.createFont(Font.TRUETYPE_FONT, inputStream);
        font = fontTmp.deriveFont(FONT_SIZE);
    }

    private void configTerminal() throws IOException {
        // Create terminal
        terminal = new DefaultTerminalFactory()
                .setInitialTerminalSize(new TerminalSize(TERMINAL_WIDTH, TERMINAL_HEIGHT))
                .setTerminalEmulatorFontConfiguration(SwingTerminalFontConfiguration.newInstance(font))
                .setTerminalEmulatorTitle(gameProperties.getLangMap().get("windowTitle"))
                .createTerminalEmulator();

        // Set icon
        InputStream inputStream = Application.class.getClassLoader()
                .getResourceAsStream("img/logo.png");
        Image icon = ImageIO.read(inputStream);
        ((SwingTerminalFrame) terminal).setIconImage(icon);

        // Create screen
        screen = new TerminalScreen(terminal);
        screen.startScreen();
    }

    private void createGUI() {
        MultiWindowTextGUI gui = new MultiWindowTextGUI(
                screen,
                new DefaultWindowManager(),
                new EmptySpace(TextColor.ANSI.BLACK)
        );

        SeparateTextGUIThread textGUIThread = (SeparateTextGUIThread) (new SeparateTextGUIThread.Factory()
                .createTextGUIThread(gui));
        textGUIThread.start();

        gameProperties.setTextGUIThread(textGUIThread);
        gameProperties.setMainThread(Thread.currentThread());

        // Thread for controlling window close
        Thread windowCloseControllThread = new Thread(
                () -> {
                    while (!gameProperties.getTextGUIThread().getState()
                            .equals(AsynchronousTextGUIThread.State.STOPPED)) {
                        try {
                            Thread.sleep(0);
                        } catch (InterruptedException e) {
                            // Auto-generated catch block
                            log.error("Error in window close controll thread: {}", e.getMessage(), e);
                        }
                    }

                    gameProperties.getMainThread().interrupt();
                });

        windowCloseControllThread.start();

        // Create window
        window = new BasicWindow();
        window.setTheme(
                SimpleTheme.makeTheme(
                        false,
                        TextColor.ANSI.BLUE_BRIGHT,
                        TextColor.ANSI.RED,
                        TextColor.ANSI.RED,
                        TextColor.ANSI.BLUE_BRIGHT,
                        TextColor.ANSI.WHITE_BRIGHT,
                        TextColor.ANSI.CYAN,
                        TextColor.ANSI.RED));

        window.setHints(Arrays.asList(Window.Hint.CENTERED, Window.Hint.NO_POST_RENDERING));
        gameProperties.setWindow(window);
        gui.addWindow(window);

        // Create content panel
        gameProperties.setContentPanel(new Panel());
        window.setComponent(gameProperties.getContentPanel());
        contentPanel = gameProperties.getContentPanel();
    }

    // Sleep
    public static void sleep(long time) throws InterruptedException {
        Thread.sleep(time);
    }

    public void start() throws InterruptedException {
        // Display oel logo
        oelLogo();

        // Prompt for should new game be created
        boolean shouldCreateNewGame = shouldCreateGameMenu();

        // TODO: Get a player amount
        int playerAmount = 2;

        // Prompt for player name
        String playerName = promptPlayerName();

        // Get game depending on whether should you create new one or join existing one
        BaseGame game;
        if (shouldCreateNewGame) {
            game = requestsCreator.createGame(playerName, playerAmount);
        }
        else {
            String gameId = promptGameId();
            game = requestsCreator.joinGame(playerName, gameId);
        }

    }

    //
    // MENUS
    //

    // Finish game
    public static void finishGame(GameProperties gameProperties, BaseGame game) throws InterruptedException {
        // Prepare new graphical settings
        Panel contentPanel = gameProperties.getContentPanel();
        contentPanel.removeAllComponents();
        contentPanel.setLayoutManager(new GridLayout(1));
        gameProperties.getWindow().setTheme(
                SimpleTheme.makeTheme(
                        false,
                        TextColor.ANSI.BLUE_BRIGHT,
                        TextColor.ANSI.BLACK,
                        TextColor.ANSI.BLACK,
                        TextColor.ANSI.BLUE_BRIGHT,
                        TextColor.ANSI.WHITE_BRIGHT,
                        TextColor.ANSI.CYAN,
                        TextColor.ANSI.BLACK));

        contentPanel.addComponent(new Label(gameProperties.getLangMap().get("gameResult") + ":"));

        contentPanel.addComponent(new EmptySpace());

        // Create table for results
        Table<String> resultsTable =
                new Table<>(
                        gameProperties.getLangMap().get("player").toUpperCase(),
                        gameProperties.getLangMap().get("loan"),
                        gameProperties.getLangMap().get("balance"));

        // Add every player to table
        for (Player player : game.getPlayers()) {
            resultsTable
                    .getTableModel()
                    .addRow(
                            player.getName(),
                            String.valueOf(player.getDebt()),
                            String.valueOf(player.getBalance()));
        }

        contentPanel.addComponent(resultsTable);

        contentPanel.addComponent(new EmptySpace());

        contentPanel.addComponent(new Label(gameProperties.getLangMap().get("congratulationsToTheWinners")));

        Confirm tmpConfirm = new Confirm();
        Button confirmButton = Elements.confirmButton(tmpConfirm, gameProperties.getLangMap().get("done"));
        contentPanel.addComponent(confirmButton);
        confirmButton.takeFocus();

        // Wait for confirmation
        tmpConfirm.waitForConfirm();
    }

    // Display OEL logo
    private void oelLogo() throws InterruptedException {
        // Prepare new graphical settings
        contentPanel.setLayoutManager(new GridLayout(1));
        window.setTheme(
                SimpleTheme.makeTheme(
                        false,
                        TextColor.ANSI.BLUE_BRIGHT,
                        TextColor.ANSI.RED,
                        TextColor.ANSI.RED,
                        TextColor.ANSI.BLUE_BRIGHT,
                        TextColor.ANSI.WHITE_BRIGHT,
                        TextColor.ANSI.CYAN,
                        TextColor.ANSI.RED));

        Game.timeBuffor();
        try {
            // Get OEL logo ASCII art
            InputStream oelLogoFileStream =
                    Application.class.getClassLoader().getResourceAsStream("arts/oel.json");
            contentPanel.addComponent(new ArtObject(Objects.requireNonNull(oelLogoFileStream)).getImageComponent());
        } catch (Exception e) {
            log.error("Error during logo reading: {}", e.getMessage(), e);
            contentPanel.addComponent(new Label("OEL"));
            contentPanel.addComponent(new Label("CR. COMP.& TRANSL. BY MI$ AL"));
            contentPanel.addComponent(new Label("REMADE IN JAVA BY ZABBUM"));
        }

        Game.sleep(5000);

        // Clean up
        contentPanel.removeAllComponents();

        // Display game motto
        contentPanel.addComponent(new Label("THE BIG GAME AND BIG MONEY."));

        Game.sleep(3000);

        // Clean up
        contentPanel.removeAllComponents();
    }

    // Ask player whether they want to connect to a game or create one
    private boolean shouldCreateGameMenu() throws InterruptedException {
        // Prepare new graphical settings
        contentPanel.setLayoutManager(new GridLayout(1));
        window.setTheme(
                SimpleTheme.makeTheme(
                        false,
                        TextColor.ANSI.BLUE_BRIGHT,
                        TextColor.ANSI.RED,
                        TextColor.ANSI.RED,
                        TextColor.ANSI.BLUE_BRIGHT,
                        TextColor.ANSI.WHITE_BRIGHT,
                        TextColor.ANSI.CYAN,
                        TextColor.ANSI.RED));

        contentPanel.addComponent(new Label("CZY CHCESZ STWORZYC NOWA GRE?"));
        contentPanel.addComponent(new EmptySpace());

        // Confirm variable
        ConfirmAction tmpConfirm = new ConfirmAction();

        var yesButton = new Button("TAK", () -> tmpConfirm.confirm("Y"));

        contentPanel.addComponent(yesButton);
        contentPanel.addComponent(
                new Button("NIE", () -> tmpConfirm.confirm("N"))
        );

        yesButton.takeFocus();

        // Wait for confirmation
        tmpConfirm.waitForConfirm();

        contentPanel.removeAllComponents();

        return tmpConfirm.getAction().equals("Y");
    }

    // Get gameId
    private String promptGameId() throws InterruptedException {
        // Prepare new graphical settings
        contentPanel.setLayoutManager(new GridLayout(1));
        window.setTheme(
                SimpleTheme.makeTheme(
                        false,
                        TextColor.ANSI.BLUE_BRIGHT,
                        TextColor.ANSI.RED,
                        TextColor.ANSI.RED,
                        TextColor.ANSI.BLUE_BRIGHT,
                        TextColor.ANSI.WHITE_BRIGHT,
                        TextColor.ANSI.CYAN,
                        TextColor.ANSI.RED));

        contentPanel.addComponent(new Label("PODAJ ADRES GRY"));
        contentPanel.addComponent(new EmptySpace());

        Panel promptPanel = new Panel(new GridLayout(2));

        // Display correct amount of textbox
        promptPanel.addComponent(new Label("?"));
        TextBox gameIdBox = new TextBox();
        promptPanel.addComponent(gameIdBox);

        contentPanel.addComponent(promptPanel);
        // Set focus to first textbox
        gameIdBox.takeFocus();

        // Confirmation button
        Confirm tmpConfirm = new Confirm();
        contentPanel.addComponent(Elements.confirmButton(tmpConfirm, gameProperties.getLangMap().get("done")));

        // Wait for confirmation
        tmpConfirm.waitForConfirm();

        log.info("GameId: {}", gameIdBox.getText());

        contentPanel.removeAllComponents();

        return gameIdBox.getText();
    }

    // Intro info for player and prompt for names
    private String promptPlayerName() throws InterruptedException {
        // Prepare new graphical settings
        contentPanel.setLayoutManager(new GridLayout(1));
        window.setTheme(
                SimpleTheme.makeTheme(
                        false,
                        TextColor.ANSI.BLUE_BRIGHT,
                        TextColor.ANSI.RED,
                        TextColor.ANSI.RED,
                        TextColor.ANSI.BLUE_BRIGHT,
                        TextColor.ANSI.WHITE_BRIGHT,
                        TextColor.ANSI.CYAN,
                        TextColor.ANSI.RED));

        contentPanel.addComponent(new Label(gameProperties.getLangMap().get("wereCurrentlyIn") + ":"));
        Game.timeBuffor();
        contentPanel.addComponent(new EmptySpace());

        // Fancy 1986
        try {
            // Get 1986 ASCII art
            InputStream startYearInputStream =
                    Application.class.getClassLoader().getResourceAsStream("arts/1986.json");
            contentPanel.addComponent(new ArtObject(startYearInputStream).getImageComponent());
        } catch (Exception e) {
            contentPanel.addComponent(new Label("1986"));
        }
        contentPanel.addComponent(new EmptySpace());

        contentPanel.addComponent(new Label(gameProperties.getLangMap().get("gameEndsIn") + " 2020"));
        contentPanel.addComponent(new Label(gameProperties.getLangMap().get("playersWillBe") + ":"));
        contentPanel.addComponent(new EmptySpace());
        contentPanel.addComponent(new Label(gameProperties.getLangMap().get("enterNames")));

        // Prompt for names
        Panel promptPanel = new Panel(new GridLayout(2));

        // Display correct amount of textbox
        promptPanel.addComponent(new Label("?"));
        TextBox playerName =
                new TextBox(gameProperties.getLangMap().get("player"));
        promptPanel.addComponent(playerName);

        contentPanel.addComponent(promptPanel);
        // Set focus to first textbox
        playerName.takeFocus();

        // Confirmation button
        Confirm tmpConfirm = new Confirm();
        contentPanel.addComponent(Elements.confirmButton(tmpConfirm, gameProperties.getLangMap().get("done")));

        // Wait for confirmation
        tmpConfirm.waitForConfirm();

        // Log player name
        log.info("Player: {}", playerName.getText());

        // Inform about money amount
        contentPanel.removeAllComponents();
        Game.timeBuffor();
        contentPanel.addComponent(
                new Label(String.format(gameProperties.getLangMap().get("everyPlayerHas"), 123421)));
        contentPanel.addComponent(new EmptySpace());

        tmpConfirm = new Confirm();
        Button confirmButton = Elements.confirmButton(tmpConfirm, gameProperties.getLangMap().get("done"));
        contentPanel.addComponent(confirmButton);
        confirmButton.takeFocus();

        // Wait for confirmation
        tmpConfirm.waitForConfirm();

        // Clean up
        contentPanel.removeAllComponents();

        return playerName.getText();
    }

    // Display main menu and get action
    public static void mainMenu(Player player, GameProperties gameProperties)
            throws InterruptedException {
        // Prepare new graphical settings
        Panel contentPanel = gameProperties.getContentPanel();
        contentPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL));
        gameProperties.getWindow().setTheme(
                SimpleTheme.makeTheme(
                        false,
                        TextColor.ANSI.WHITE_BRIGHT,
                        TextColor.ANSI.MAGENTA,
                        TextColor.ANSI.MAGENTA,
                        TextColor.ANSI.WHITE_BRIGHT,
                        TextColor.ANSI.WHITE_BRIGHT,
                        TextColor.ANSI.CYAN,
                        TextColor.ANSI.MAGENTA));

        // Create theme for black buttons not to make them different when selected
        Theme blackButton =
                SimpleTheme.makeTheme(
                        false,
                        TextColor.ANSI.BLACK,
                        TextColor.ANSI.MAGENTA,
                        TextColor.ANSI.BLACK,
                        TextColor.ANSI.WHITE_BRIGHT,
                        TextColor.ANSI.WHITE_BRIGHT,
                        TextColor.ANSI.CYAN,
                        TextColor.ANSI.MAGENTA);

        // Display options
        contentPanel.addComponent(
                new Label(gameProperties.getLangMap().get("itsYourDecision"))
                        .setTheme(new SimpleTheme(TextColor.ANSI.MAGENTA, TextColor.ANSI.WHITE_BRIGHT)));

        contentPanel.addComponent(new EmptySpace());

        contentPanel.addComponent(
                new Label(
                        gameProperties.getLangMap().get("player").toUpperCase()
                                + ": "
                                + player.getName()
                                + " $= "
                                + player.getBalance())
                        .setTheme(new SimpleTheme(TextColor.ANSI.CYAN_BRIGHT, TextColor.ANSI.MAGENTA)));

        contentPanel.addComponent(new EmptySpace());

        // Confirm variable
        ConfirmAction tmpConfirm = new ConfirmAction();

        // Options pt. 1
        contentPanel.addComponent(
                new Label(" " + gameProperties.getLangMap().get("buying") + " ")
                        .setTheme(new SimpleTheme(TextColor.ANSI.MAGENTA, TextColor.ANSI.BLUE)));

        Component firstButton =
                new Button(
                        gameProperties.getLangMap().get("drillsIndustries"),
                        () -> tmpConfirm.confirm("A"))
                        .setTheme(blackButton);
        contentPanel.addComponent(firstButton);
        ((Interactable) firstButton).takeFocus();

        contentPanel.addComponent(
                new Button(
                        gameProperties.getLangMap().get("pumpsIndustries"),
                        () -> tmpConfirm.confirm("B")));
        contentPanel.addComponent(
                new Button(
                        gameProperties.getLangMap().get("carsIndustries"),
                        () -> tmpConfirm.confirm("C"))
                        .setTheme(blackButton));
        contentPanel.addComponent(
                new Button(
                        gameProperties.getLangMap().get("oilfields"),
                        () -> tmpConfirm.confirm("D")));
        contentPanel.addComponent(
                new Button(
                        gameProperties.getLangMap().get("drills"),
                        () -> tmpConfirm.confirm("E"))
                        .setTheme(blackButton));
        contentPanel.addComponent(
                new Button(
                        gameProperties.getLangMap().get("pumps"),
                        () -> tmpConfirm.confirm("F")));
        contentPanel.addComponent(
                new Button(
                        gameProperties.getLangMap().get("cars"),
                        () -> tmpConfirm.confirm("G"))
                        .setTheme(blackButton));

        // Space
        contentPanel.addComponent(new EmptySpace());

        // Options pt. 2
        contentPanel.addComponent(
                new Label(" " + gameProperties.getLangMap().get("otherPossibilities") + " ")
                        .setTheme(new SimpleTheme(TextColor.ANSI.MAGENTA, TextColor.ANSI.BLUE)));

        contentPanel.addComponent(
                new Button(
                        gameProperties.getLangMap().get("nextPlayer"),
                        () -> tmpConfirm.confirm("H")));
        contentPanel.addComponent(
                new Button(
                        gameProperties.getLangMap().get("attemptSabotage"),
                        () -> tmpConfirm.confirm("I"))
                        .setTheme(blackButton));
        contentPanel.addComponent(
                new Button(
                        gameProperties.getLangMap().get("changePrices"),
                        () -> tmpConfirm.confirm("J")));

        tmpConfirm.waitForConfirm();
        contentPanel.removeAllComponents();

        // Redirect to the valid menu
        switch (tmpConfirm.getAction()) {
            case "A" -> // Drills productions
            {
            }
            case "B" -> // Pumps productions
            {
            }
            case "C" -> // Cars productions
            {
            }
            case "D" -> // Oilfields
            {
            }
            case "E" -> // Drills
            {
            }
            case "F" -> // Pumps
            {
            }
            case "G" -> // Cars
            {
            }
            case "H" -> // Pass
            {
            }
            case "I" -> // Attempt sabotage
            {
            }
            case "J" -> // Change prices
            {
            }
            default -> System.out.println("No value provided. This could be an error.");
        }
    }

    public static void timeBuffor() throws InterruptedException {
        Thread.sleep(1);
    }
}
