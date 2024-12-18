package com.github.zabbum.oelremakeclient;

import com.github.zabbum.oelremakeclient.arguments.Arguments;
import com.github.zabbum.oelremakeclient.arguments.CliArgumentsParser;
import com.github.zabbum.oelremakeclient.artloader.ArtObject;
import com.github.zabbum.oelrlib.Player;
import com.github.zabbum.oelrlib.game.BaseGame;
import com.github.zabbum.oelrlib.game.GameStatus;
import com.github.zabbum.oelrlib.plants.industries.AbstractIndustry;
import com.github.zabbum.oelrlib.plants.oilfield.Oilfield;
import com.github.zabbum.oelrlib.requests.*;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.SimpleTheme;
import com.googlecode.lanterna.graphics.Theme;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.Component;
import com.googlecode.lanterna.gui2.GridLayout;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.table.Table;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import com.googlecode.lanterna.terminal.swing.SwingTerminalFontConfiguration;
import com.googlecode.lanterna.terminal.swing.SwingTerminalFrame;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompSessionHandler;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Pattern;

@Slf4j
public class Game {
    private static final float FONT_SIZE = 15.0f;
    private static final int TERMINAL_WIDTH = 60;
    private static final int TERMINAL_HEIGHT = 34;

    private final Arguments arguments;
    private final String wsEndpointUrl;
    private final String httpEndpointUrl;
    @Getter
    private GameProperties gameProperties;
    private Font font;
    @Getter
    private Screen screen;
    private Window window;
    private MultiWindowTextGUI gui;
    private Panel contentPanel;
    private RequestsCreator requestsCreator;
    private BaseGame baseGame;
    private int playerId;
    private Player player;
    private final Display display = new Display();
    private Map<String, String> langMap;
    private OelRequest oelRequest;

    public Game(String[] args) throws IOException, FontFormatException {
        this.arguments = CliArgumentsParser.parseArguments(args);

        wsEndpointUrl = "ws://"+arguments.getServerAddress()+"/base-game";
        httpEndpointUrl = "http://"+arguments.getServerAddress()+"/baseGame";

        setUp();
    }

    private void setUp() throws IOException, FontFormatException {
        // Create gameProperties
        gameProperties = new GameProperties();

        setLanguage();
        setFont();
        configTerminal();
        createGUI();

        requestsCreator = new RequestsCreator(wsEndpointUrl, httpEndpointUrl);
    }

    private void setLanguage() throws IOException {
        String langCode = arguments.getLang();

        InputStream inputStream = Application.class.getClassLoader()
                .getResourceAsStream("lang/" + langCode + ".json");
        gameProperties.setLangMap(LangExtractor.getLangData(inputStream));
        langMap = gameProperties.getLangMap();
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
        Terminal terminal = new DefaultTerminalFactory()
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
        gui = new MultiWindowTextGUI(
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

    public void start() throws InterruptedException {
        // Display oel logo
        if (!arguments.getDevMode())
            display.oelLogo();

        // Prompt for should new game be created
        boolean shouldCreateNewGame = shouldCreateGameMenu();

        // TODO: Get a player amount
        int playerAmount = 2;

        // Prompt for player name
        String playerName = promptPlayerName();

        // Get game depending on whether should you create new one or join existing one
        if (shouldCreateNewGame) {
            oelRequest = StarterRequest.builder()
                    .playerName(playerName)
                    .playersAmount(playerAmount)
                    .build();
            baseGame = requestsCreator.oelRequest(oelRequest);
        } else {
            String gameId = promptGameId();
            oelRequest = JoinRequest.builder()
                    .gameId(gameId)
                    .playerName(playerName)
                    .build();
            baseGame = requestsCreator.oelRequest(oelRequest);
        }
        playerId = baseGame.getPlayers().size() - 1;

        do {
            // Waiting menu
            waitingMenu();

            // Main menu
            mainMenu();
        }
        while (!baseGame.getGameStatus().equals(GameStatus.FINISHED));

        // TODO: Waiting and main menu loop
    }

    /**
     * Waiting menu handling
     */
    private void waitingMenu() {
        display.waitingMenu();

        // Websocket connection
        StandardWebSocketClient client = new StandardWebSocketClient();
        WebSocketStompClient stompClient = new WebSocketStompClient(client);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        BlockingQueue<BaseGame> gamesQueue = new LinkedBlockingQueue<>();
        StompSessionHandler sessionHandler = new WaitStompSessionHandler(gamesQueue, baseGame);

        stompClient.connectAsync(wsEndpointUrl, sessionHandler);

        while (true) {
            try {
                gui.updateScreen();
                BaseGame response = gamesQueue.take();
                log.info("Processing response.");

                baseGame = response;
                display.waitingMenu();

                if (baseGame.getCurrentPlayerTurn() == playerId)
                    break;
            } catch (InterruptedException e) {
                log.error("Thread interrupted while waiting for messages.");
                Thread.currentThread().interrupt();
                break;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // main menu with interactions
    private void mainMenu() throws InterruptedException {
        player = baseGame.getPlayers().get(playerId);

        MenuResponse menuAction;

        do {
            // Display menu and get menuAction from player
            menuAction = display.mainMenu();

            // Display correct menu
            switch (menuAction) {
                case DRILLS_INDUSTRIES -> {
                    log.info("Drills industries buy menu");
                    menuAction = display.buyIndustryMenu(
                            baseGame.getDrillsIndustries(),
                            TextColor.ANSI.WHITE_BRIGHT, TextColor.ANSI.BLUE,
                            TextColor.ANSI.BLUE, TextColor.ANSI.WHITE_BRIGHT,
                            TextColor.ANSI.WHITE_BRIGHT, TextColor.ANSI.CYAN,
                            TextColor.ANSI.BLUE,
                            langMap.get("drillsIndustrySale"),
                            langMap.get("drillsIndustryPrompt"),
                            langMap.get("drillsPricePrompt"),
                            60000
                    );
                }
                case PUMPS_INDUSTRIES -> {
                    log.info("Pumps industries buy menu");
                    menuAction = display.buyIndustryMenu(
                            baseGame.getPumpsIndustries(),
                            TextColor.ANSI.WHITE_BRIGHT, TextColor.ANSI.BLUE,
                            TextColor.ANSI.BLUE, TextColor.ANSI.WHITE_BRIGHT,
                            TextColor.ANSI.WHITE_BRIGHT, TextColor.ANSI.CYAN,
                            TextColor.ANSI.BLUE,
                            langMap.get("pumpsIndustrySale"),
                            langMap.get("pumpsIndustryPrompt"),
                            langMap.get("pumpsPricePrompt"),
                            50000
                    );
                }
                case CARS_INDUSTRIES -> {
                    log.info("Cars industries buy menu");
                    menuAction = display.buyIndustryMenu(
                            baseGame.getCarsIndustries(),
                            TextColor.ANSI.WHITE_BRIGHT, TextColor.ANSI.BLUE,
                            TextColor.ANSI.BLUE, TextColor.ANSI.WHITE_BRIGHT,
                            TextColor.ANSI.WHITE_BRIGHT, TextColor.ANSI.CYAN,
                            TextColor.ANSI.BLUE,
                            langMap.get("carsIndustrySale"),
                            langMap.get("carsIndustryPrompt"),
                            langMap.get("carsPricePrompt"),
                            50000
                    );
                }
                case OILFIELDS -> {
                    log.info("Oilfield buy menu");
                    menuAction = display.buyOilfieldMenu();
                }
                case DRILLS -> {
                    log.info("Drills buying menu");
                    menuAction = display.buyProductsMenu(
                            baseGame.getDrillsIndustries(),
                            TextColor.ANSI.WHITE_BRIGHT, TextColor.ANSI.YELLOW,
                            TextColor.ANSI.BLUE, TextColor.ANSI.WHITE_BRIGHT,
                            TextColor.ANSI.WHITE_BRIGHT, TextColor.ANSI.CYAN,
                            TextColor.ANSI.YELLOW,
                            TextColor.ANSI.BLUE,
                            langMap.get("drillsHereYouCanBuy"),
                            langMap.get("drillsProductsAmountPrompt"),
                            10
                    );
                }
                case PUMPS -> {
                    log.info("Pumps buying menu");
                    menuAction = display.buyProductsMenu(
                            baseGame.getPumpsIndustries(),
                            TextColor.ANSI.WHITE_BRIGHT, TextColor.ANSI.BLACK_BRIGHT,
                            TextColor.ANSI.BLACK_BRIGHT, TextColor.ANSI.WHITE_BRIGHT,
                            TextColor.ANSI.WHITE_BRIGHT, TextColor.ANSI.CYAN,
                            TextColor.ANSI.BLACK_BRIGHT,
                            TextColor.ANSI.BLACK,
                            langMap.get("pumpsHereYouCanBuy"),
                            langMap.get("pumpsProductsAmountPrompt"),
                            15
                    );
                }
                case CARS -> {
                    log.info("Cars buying menu");
                    menuAction = display.buyProductsMenu(
                            baseGame.getCarsIndustries(),
                            TextColor.ANSI.WHITE_BRIGHT, TextColor.ANSI.RED_BRIGHT,
                            TextColor.ANSI.RED_BRIGHT, TextColor.ANSI.WHITE_BRIGHT,
                            TextColor.ANSI.WHITE_BRIGHT, TextColor.ANSI.CYAN,
                            TextColor.ANSI.RED_BRIGHT,
                            TextColor.ANSI.BLACK,
                            langMap.get("carsHereYouCanBuy"),
                            langMap.get("carsProductsAmountPrompt"),
                            15
                    );
                }
                default -> {
                    log.warn("Unhandled menuAction: {}", menuAction);
                }
            }
        }
        while (Objects.equals(menuAction, MenuResponse.MAIN_MENU));

        // If something went wrong, log error and return
        if (!menuAction.equals(MenuResponse.SUCCESS)) {
            log.error("Menu action code: {}", menuAction);
            menuAction = MenuResponse.PASS;
            oelRequest = PassRequest.builder()
                    .gameId(baseGame.getGameId())
                    .playerId(playerId)
                    .build();
        }

        baseGame = requestsCreator.oelRequest(oelRequest);
    }

    // Ask player whether they want to connect to a game or create one
    private boolean shouldCreateGameMenu() throws InterruptedException {
        // Prepare new graphical settings
        contentPanel.setLayoutManager(new GridLayout(1));
        window.setTheme(
                SimpleTheme.makeTheme(
                        false,
                        TextColor.ANSI.BLUE_BRIGHT, TextColor.ANSI.RED,
                        TextColor.ANSI.RED, TextColor.ANSI.BLUE_BRIGHT,
                        TextColor.ANSI.WHITE_BRIGHT, TextColor.ANSI.CYAN,
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

    public static void timeBuffor() throws InterruptedException {
        Thread.sleep(1);
    }

    /**
     * Class with all frontend part
     */
    private class Display {

        public MenuResponse buyOilfieldMenu() throws InterruptedException {
            contentPanel.removeAllComponents();
            contentPanel.setLayoutManager(new GridLayout(1));
            window.setTheme(
                    SimpleTheme.makeTheme(
                            false,
                            TextColor.ANSI.BLACK, TextColor.ANSI.BLUE_BRIGHT,
                            TextColor.ANSI.YELLOW_BRIGHT, TextColor.ANSI.BLUE,
                            TextColor.ANSI.WHITE_BRIGHT, TextColor.ANSI.CYAN,
                            TextColor.ANSI.BLUE_BRIGHT));

            // Display title and player details
            Panel titlePanel = new Panel(new GridLayout(1));
            titlePanel.setTheme(
                    new SimpleTheme(
                            TextColor.ANSI.BLUE_BRIGHT, TextColor.ANSI.YELLOW_BRIGHT));
            titlePanel.addComponent(new EmptySpace());
            titlePanel.addComponent(
                    new Label(
                            langMap.get("oilfieldsSale")));
            titlePanel.addComponent(
                    new Label(
                            langMap.get("balance2")
                                    + ": "
                                    + String.valueOf(player.getBalance())
                                    + "$"));
            titlePanel.addComponent(new EmptySpace());
            contentPanel.addComponent(titlePanel);

            contentPanel.addComponent(new EmptySpace());

            // Panel for oifields and details
            Panel oilPanel = new Panel(new GridLayout(2));

            // Panel for old ownerships
            Panel oldOwnershipPanel = new Panel(new GridLayout(1));
            oldOwnershipPanel.setTheme(
                    new SimpleTheme(
                            TextColor.ANSI.YELLOW_BRIGHT, TextColor.ANSI.BLUE_BRIGHT));
            oldOwnershipPanel.addComponent(
                    new Label(
                            langMap.get("oldOwnership")));
            oldOwnershipPanel.addComponent(new EmptySpace());
            oldOwnershipPanel.addComponent(new Label("1-2: SMAR & CO."));
            oldOwnershipPanel.addComponent(new Label("3-4: R.R. INC."));
            oldOwnershipPanel.addComponent(new Label("5-6: O. MACHINEN"));
            oldOwnershipPanel.addComponent(new Label("7-8: GUT & LUT"));
            oldOwnershipPanel.addComponent(new Label("9-10: OLEJARZ KC"));
            oldOwnershipPanel.addComponent(new Label("11-12: ZDISEK OB."));

            oilPanel.addComponent(oldOwnershipPanel);

            // Panel for oilfields
            Panel oilfieldsPanel = new Panel(new GridLayout(1));

            // Create table
            Table<String> oilfieldsTable = new Table<String>(
                    "NR",
                    langMap.get("name"),
                    langMap.get("price"));

            // Add every available oilfield to table
            List<Oilfield> oilfields = baseGame.getOilfields();
            oilfieldsTable.getTableModel().addRow("0", "-", "-");
            for (int oilfieldIndex = 0; oilfieldIndex < oilfields.size(); oilfieldIndex++) {
                if (!oilfields.get(oilfieldIndex).isBought()) {
                    // If oilfield is not bought, make it possible to buy it
                    oilfieldsTable
                            .getTableModel()
                            .addRow(
                                    String.valueOf(oilfieldIndex + 1),
                                    oilfields.get(oilfieldIndex).getName(),
                                    String.valueOf(oilfields.get(oilfieldIndex).getPlantPrice()) + "$");
                }
            }

            Confirm tmpConfirm = new Confirm();
            oilfieldsTable.setSelectAction(tmpConfirm::confirm);

            // Display table
            oilfieldsPanel.addComponent(oilfieldsTable);
            oilPanel.addComponent(oilfieldsPanel);

            // Display oilfield details
            contentPanel.addComponent(oilPanel);
            oilfieldsTable.takeFocus();
            contentPanel.addComponent(new EmptySpace());
            contentPanel.addComponent(new Label(langMap.get("oilfieldsPrompt")));

            // Wait for selection
            tmpConfirm.waitForConfirm();
            tmpConfirm = null;
            oilfieldsTable.setEnabled(false);
            int selectedOilfieldIndex = Integer.parseInt(
                    oilfieldsTable.getTableModel().getRow(oilfieldsTable.getSelectedRow()).get(0))
                    - 1;

            // If 0 selected, return
            if (selectedOilfieldIndex == -1) {
                // Clean up
                contentPanel.removeAllComponents();
                return MenuResponse.MAIN_MENU;
            }

            // Build purchase request
            oelRequest = BuyOilfieldRequest.builder()
                    .gameId(baseGame.getGameId())
                    .oilfieldId(selectedOilfieldIndex)
                    .playerId(playerId)
                    .build();

            return MenuResponse.SUCCESS;
        }

        public MenuResponse buyProductsMenu(
                List<? extends AbstractIndustry> industries,
                TextColor baseForeground, TextColor baseBackground,
                TextColor editableForeground, TextColor editableBackground,
                TextColor selectedForeground, TextColor selectedBackground,
                TextColor guiBackground,
                TextColor tableForeground,
                String hereYouCanBuy, String productsAmountPrompt,
                int maxAmount
        ) throws InterruptedException {
            contentPanel.removeAllComponents();
            contentPanel.setLayoutManager(new GridLayout(1));
            window.setTheme(SimpleTheme.makeTheme(
                    false,
                    baseForeground, baseBackground,
                    editableForeground, editableBackground,
                    selectedForeground, selectedBackground,
                    guiBackground));

            // Display title
            Panel titlePanel = new Panel(new GridLayout(1));
            titlePanel.setTheme(new SimpleTheme(baseBackground, baseForeground));
            titlePanel.addComponent(new EmptySpace());
            titlePanel.addComponent(new Label(hereYouCanBuy));
            titlePanel.addComponent(
                    new Label(
                            langMap.get("balance2")
                                    + ": "
                                    + player.getBalance()
                                    + "$"));
            titlePanel.addComponent(new EmptySpace());
            contentPanel.addComponent(titlePanel);

            contentPanel.addComponent(new EmptySpace());

            // Create table
            Table<String> productsTable =
                    new Table<>(
                            "NR",
                            langMap.get("industryName"),
                            langMap.get("productsAmount"),
                            langMap.get("price"));
            productsTable.setTheme(
                    SimpleTheme.makeTheme(
                            false,
                            tableForeground, baseBackground,
                            editableForeground, editableBackground,
                            selectedForeground, selectedBackground,
                            guiBackground));

            // Add every available industry to table
            productsTable.getTableModel().addRow("0", "-", "-", "-");
            for (int industryIndex = 0; industryIndex < industries.size(); industryIndex++) {
                if (industries.get(industryIndex).isBought()
                        && industries.get(industryIndex).getProductsAmount() != 0) {
                    // If industry is bought and has products,
                    // make it possible to buy it
                    productsTable
                            .getTableModel()
                            .addRow(
                                    String.valueOf(industryIndex + 1),
                                    industries.get(industryIndex).getName(),
                                    String.valueOf(industries.get(industryIndex).getProductsAmount()),
                                    industries.get(industryIndex).getProductPrice() + "$");
                }
            }

            Confirm tmpConfirm = new Confirm();
            productsTable.setSelectAction(tmpConfirm::confirm);

            // Display table
            contentPanel.addComponent(productsTable);
            productsTable.takeFocus();
            contentPanel.addComponent(new EmptySpace());
            contentPanel.addComponent(new Label(langMap.get("whereToBuy")));

            // Wait for selection
            tmpConfirm.waitForConfirm();
            tmpConfirm = null;
            int selectedIndustryIndex =
                    Integer.parseInt(
                            productsTable.getTableModel().getRow(productsTable.getSelectedRow()).get(0))
                            - 1;

            // If 0 selected, return
            if (selectedIndustryIndex == -1) {
                // Clean up
                contentPanel.removeAllComponents();
                return MenuResponse.MAIN_MENU;
            }

            // Prompt for product amount
            contentPanel.addComponent(new Label(productsAmountPrompt));

            // Prompt for product amount until provided value is valid
            TextBox productAmountBox;
            int selectedProductAmount = -1;
            do {
                // Prompt for product amount
                contentPanel.addComponent(new EmptySpace());
                productAmountBox = new TextBox(new TerminalSize(6, 1));
                productAmountBox.setValidationPattern(Pattern.compile("[0-9]*"));
                contentPanel.addComponent(productAmountBox);
                tmpConfirm = new Confirm();
                contentPanel.addComponent(Elements.confirmButton(tmpConfirm, langMap.get("done")));

                // Wait for selection
                productAmountBox.takeFocus();
                tmpConfirm.waitForConfirm();

                try {
                    selectedProductAmount = Integer.parseInt(productAmountBox.getText());
                } catch (NumberFormatException e) {
                    // If a bad value has been provided
                    selectedProductAmount = -1;
                }
            } while (selectedProductAmount < 0 || selectedProductAmount > maxAmount);

            // Prompt for oilfield
            contentPanel.addComponent(new EmptySpace());
            contentPanel.addComponent(new Label(langMap.get("onWhatOilfield")));

            // Display all the oilfields

            // Create table
            Table<String> oilfieldsTable =
                    new Table<>(
                            "NR", langMap.get("name"), langMap.get("property"));

            // Add every available oilfield to table
            List<Oilfield> oilfields = baseGame.getOilfields();
            oilfieldsTable.getTableModel().addRow("0", "-", "-");
            for (int oilfieldIndex = 0; oilfieldIndex < oilfields.size(); oilfieldIndex++) {
                // If oilfield is bought, display the name
                String ownerName = "---";
                if (oilfields.get(oilfieldIndex).isBought()) {
                    ownerName = oilfields.get(oilfieldIndex).getOwnership().getName();
                }
                oilfieldsTable
                        .getTableModel()
                        .addRow(
                                String.valueOf(oilfieldIndex + 1),
                                oilfields.get(oilfieldIndex).getName(),
                                ownerName);
            }

            tmpConfirm = new Confirm();
            oilfieldsTable.setSelectAction(tmpConfirm::confirm);

            // Display table
            contentPanel.addComponent(oilfieldsTable);
            oilfieldsTable.takeFocus();

            // Wait for selection
            tmpConfirm.waitForConfirm();
            int selectedOilfieldIndex =
                    Integer.parseInt(
                            oilfieldsTable.getTableModel().getRow(oilfieldsTable.getSelectedRow()).get(0))
                            - 1;

            // If 0 selected, return
            if (selectedOilfieldIndex == -1) {
                // Clean up
                contentPanel.removeAllComponents();
                return MenuResponse.MAIN_MENU;
            }

            // Build purchase request
            oelRequest = BuyProductsRequest.builder()
                    .gameId(baseGame.getGameId())
                    .playerId(playerId)
                    .industryClassName(industries.get(0).getClass().getName())
                    .industryId(selectedIndustryIndex)
                    .oilfieldId(selectedOilfieldIndex)
                    .productAmount(selectedProductAmount)
                    .build();

            return MenuResponse.SUCCESS;
        }

        public MenuResponse buyIndustryMenu(
                List<? extends AbstractIndustry> industries,
                TextColor baseForeground, TextColor baseBackground,
                TextColor editableForeground, TextColor editableBackground,
                TextColor selectedForeground, TextColor selectedBackground,
                TextColor guiBackground,
                String industrySale, String industryPrompt, String pricePrompt,
                int maxPrice
        ) throws InterruptedException {
            contentPanel.removeAllComponents();
            contentPanel.setLayoutManager(new GridLayout(1));
            window.setTheme(SimpleTheme.makeTheme(
                    false,
                    baseForeground, baseBackground,
                    editableForeground, editableBackground,
                    selectedForeground, selectedBackground,
                    guiBackground));

            // Display title
            Panel titlePanel = new Panel(new GridLayout(1));
            titlePanel.setTheme(new SimpleTheme(baseBackground, baseForeground));
            titlePanel.addComponent(new EmptySpace());
            titlePanel.addComponent(new Label(industrySale));
            titlePanel.addComponent(
                    new Label(
                            langMap.get("balance2")
                                    + ": "
                                    + player.getBalance()
                                    + "$"));
            titlePanel.addComponent(new EmptySpace());
            contentPanel.addComponent(titlePanel);

            contentPanel.addComponent(new EmptySpace());

            // Create table
            Table<String> industriesTable =
                    new Table<>(
                            "NR",
                            langMap.get("industryName"),
                            langMap.get("productsAmount"),
                            langMap.get("price"));

            // Add every available industry to table
            industriesTable.getTableModel().addRow("0", "-", "-", "-");
            for (int industryIndex = 0; industryIndex < industries.size(); industryIndex++) {
                if (!industries.get(industryIndex).isBought()) {
                    // If industry is not bought, make it possible to buy it
                    industriesTable
                            .getTableModel()
                            .addRow(
                                    String.valueOf(industryIndex + 1),
                                    industries.get(industryIndex).getName(),
                                    String.valueOf(industries.get(industryIndex).getProductsAmount()),
                                    industries.get(industryIndex).getPlantPrice() + "$");
                }
            }

            Confirm tmpConfirm = new Confirm();
            industriesTable.setSelectAction(tmpConfirm::confirm);

            // Display table
            contentPanel.addComponent(industriesTable);
            industriesTable.takeFocus();
            contentPanel.addComponent(new EmptySpace());
            contentPanel.addComponent(new Label(industryPrompt));

            // Wait for selection
            tmpConfirm.waitForConfirm();
            industriesTable.setEnabled(false);
            int selectedIndustryIndex =
                    Integer.parseInt(
                            industriesTable.getTableModel().getRow(industriesTable.getSelectedRow()).get(0))
                            - 1;

            // If 0 selected, return
            if (selectedIndustryIndex == -1) {
                // Clean up
                contentPanel.removeAllComponents();
                return MenuResponse.MAIN_MENU;
            }

            // Inform user about purchase
            contentPanel.addComponent(new EmptySpace());
            contentPanel.addComponent(
                    new Label(langMap.get("youAreOwnerOfIndustry") + ": "));
            contentPanel.addComponent(new Label(industries.get(selectedIndustryIndex).getName()));
            contentPanel.addComponent(new EmptySpace());
            contentPanel.addComponent(new Label(pricePrompt));

            // Prompt for price until provided value is valid
            TextBox productPriceBox = new TextBox(new TerminalSize(6, 1));
            productPriceBox.setValidationPattern(Pattern.compile("[0-9]*"));
            contentPanel.addComponent(productPriceBox);
            productPriceBox.takeFocus();

            tmpConfirm = new Confirm();
            contentPanel.addComponent(Elements.confirmButton(tmpConfirm, langMap.get("done")));

            // If confirm button is pressed and choice is valid, let it be
            do {
                tmpConfirm.waitForConfirm();
                tmpConfirm.setConfirmStatus(false);
            } while (!(SimpleLogic.isValid(productPriceBox.getText(), 0, new int[]{maxPrice})));


            // Build purchase request
            oelRequest = BuyIndustryRequest.builder()
                    .gameId(baseGame.getGameId())
                    .industryId(selectedIndustryIndex)
                    .playerId(playerId)
                    .industryClassName(industries.get(0).getClass().getName())
                    .productPrice(Integer.valueOf(productPriceBox.getText()))
                    .build();

            return MenuResponse.SUCCESS;
        }

        /**
         * Main menu
         *
         * @throws InterruptedException Thread.sleep() is used, so interrupted exception may naturally occur
         */
        public MenuResponse mainMenu()
                throws InterruptedException {
            // Prepare new graphical settings
            contentPanel.removeAllComponents();
            contentPanel.setLayoutManager(new GridLayout(1));
            window.setTheme(
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
            return switch (tmpConfirm.getAction()) {
                case "A" -> MenuResponse.DRILLS_INDUSTRIES;
                case "B" -> MenuResponse.PUMPS_INDUSTRIES;
                case "C" -> MenuResponse.CARS_INDUSTRIES;
                case "D" -> MenuResponse.OILFIELDS;
                case "E" -> MenuResponse.DRILLS;
                case "F" -> MenuResponse.PUMPS;
                case "G" -> MenuResponse.CARS;
                case "H" -> MenuResponse.PASS;
                case "I" -> MenuResponse.SABOTAGE;
                case "J" -> MenuResponse.CHANGE_PRICES;
                default -> {
                    log.error("No/wrong value provided in menu.");
                    yield null;
                }
            };
        }

        /**
         * Waiting menu
         */
        public void waitingMenu() {
            contentPanel.removeAllComponents();
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

            contentPanel.addComponent(new EmptySpace());
            contentPanel.addComponent(new Label("ID: " + baseGame.getGameId()));
            if (baseGame.getGameStatus().equals(GameStatus.IN_PROGRESS)) {
                contentPanel.addComponent(new Label("CZEKANIE NA TURE"));
            } else {
                contentPanel.addComponent(new Label("CZEKANIE NA GRACZY."));
            }
            contentPanel.addComponent(new EmptySpace());

            Panel playersPanel = new Panel(new GridLayout(2));
            contentPanel.addComponent(playersPanel);

            for (int i = 0; i < baseGame.getPlayersAmount(); i++) {
                playersPanel.addComponent(new Label(String.valueOf(i + 1)));

                String playerName = "...";
                try {
                    playerName = baseGame.getPlayers().get(i).getName();
                } catch (IndexOutOfBoundsException ignored) {
                }

                Label playerLabel = new Label(playerName);

                if (baseGame.getGameStatus() == GameStatus.IN_PROGRESS) {
                    if (baseGame.getCurrentPlayerTurn() == i) {
                        playerLabel.setTheme(new SimpleTheme(TextColor.ANSI.WHITE_BRIGHT, TextColor.ANSI.CYAN));
                    }
                }

                playersPanel.addComponent(playerLabel);
            }

            playersPanel.addComponent(new EmptySpace());

            try {
                gui.updateScreen();
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }

        /**
         * Display oel logo
         *
         * @throws InterruptedException Thread.sleep() is used, so interrupted exception may naturally occur
         */
        public void oelLogo() throws InterruptedException {
            // Prepare new graphical settings
            contentPanel.setLayoutManager(new GridLayout(1));
            window.setTheme(
                    SimpleTheme.makeTheme(
                            false,
                            TextColor.ANSI.BLUE_BRIGHT, TextColor.ANSI.RED,
                            TextColor.ANSI.RED, TextColor.ANSI.BLUE_BRIGHT,
                            TextColor.ANSI.WHITE_BRIGHT, TextColor.ANSI.CYAN,
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

            Thread.sleep(5000);

            // Clean up
            contentPanel.removeAllComponents();

            // Display game motto
            contentPanel.addComponent(new Label("THE BIG GAME AND BIG MONEY."));

            Thread.sleep(3000);

            // Clean up
            contentPanel.removeAllComponents();
        }
    }

    public enum MenuResponse {
        DRILLS_INDUSTRIES, PUMPS_INDUSTRIES, CARS_INDUSTRIES, OILFIELDS,
        DRILLS, PUMPS, CARS,
        SABOTAGE, CHANGE_PRICES,
        MAIN_MENU,
        PASS,
        SUCCESS
    }
}
