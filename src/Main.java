import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

public class Main extends Application {

    public static double dataGenStart;
    public static double dataGenEnd;
    public static double dataGenIncrement;
    public static List<Integer> hiddenLayers;
    public static int dimension;
    private static Stage primaryStage;
    private static GUIController controller;
    private static Network network;
    private static boolean shouldStop = false;
    public static boolean shouldPause = false;
    private static boolean useGUI = true;
    private static boolean isRadialBasis = false;
    private static String savePath;
    private static Thread networkRun;
    private static javax.swing.Timer timer;
    private static double progress = 0;

    public static void start(double dataGenStart, double dataGenEnd, double dataGenIncrement, List<Integer> hiddenLayers, int inputCount, boolean isRadialBasis) {
        System.out.println("Starting");

        //Create network with examples from data generation
        network = new Network(hiddenLayers, inputCount, isRadialBasis, generateData(dataGenStart, dataGenEnd, dataGenIncrement, inputCount, Network::rosenbrock));

        System.out.println("Created network");

        System.out.println("Starting to run network");

        if (useGUI) {
            networkRun = new Thread(network);
            networkRun.start();
        } else {
            network.run();
        }

        // "Test" the progress bar
        if (useGUI) {
            timer = new javax.swing.Timer(1, ae -> {
                if ((int) (Math.random() * 4) != 0 && progress <= 0.97d)
                    controller.progressBar.setProgress(progress += 0.0001d);
                if (shouldStop)
                    timer.stop();
            });

            timer.start();
        }
    }

    /**
     * Generates examples of given function
     *
     * @param dataGenStart          Start of range for data
     * @param dataGenEnd            End of range for data
     * @param dataGenIncrement      How much to increment between each data point
     * @param dimension             How many dimensions to generate data in
     * @param functionToApproximate Function to generate outputs from
     * @return List of examples of given function in given number of dimensions through range given, with given increment
     */
    private static List<Example> generateData(double dataGenStart, double dataGenEnd, double dataGenIncrement, int dimension, Function<double[], Double> functionToApproximate) {
        System.out.println("Starting data generation");
        List<Example> examples = new ArrayList<Example>();
        double range = Math.abs(dataGenEnd - dataGenStart);
        int numExamples = (int) Math.pow((range / dataGenIncrement), (double) dimension);

        // Create List with appropriate number of examples
        for (int i = 0; i < numExamples; i++) {
            examples.add(new Example());
        }

        // Initialize for lists to have space for inputs
        examples.parallelStream().forEach(example -> {
            for (int i = 0; i < dimension; i++) {
                example.inputs.add(0d);
            }
        });

        // Create point counter and initialize
        List<Double> point = new ArrayList<Double>();
        for (int i = 0; i < dimension; i++) {
            point.add(dataGenStart);
        }

        System.out.println("Starting to count");
        for (int i = 0; i < numExamples; i++) {

            // Move data from point to separate list to not modify dimensions of point
            List<Double> calculatedPoint = new ArrayList<Double>();
            for (int j = 0; j < dimension; j++) {
                calculatedPoint.add(point.get(j));
            }

            // Calculate output and add to end of list
            double[] inputs = calculatedPoint.stream().mapToDouble(Double::doubleValue).toArray();
            Double functionOutput = functionToApproximate.apply(inputs);


            List<Double> outputs = new ArrayList<>();
            outputs.add(functionOutput);
            Example ex = new Example(calculatedPoint, outputs);

            examples.set(i, ex); // Add calculated point as example
            boolean carry = true; // Carry flag for arithmetic ahead

            for (int k = dimension - 1; k >= 0; k--) {
                if (carry) {
                    // If over dataGenEnd, carry flag stays set and current dimension is set to dataGenStart
                    if (point.get(k) + dataGenIncrement > dataGenEnd)
                        point.set(k, dataGenStart);
                    else {
                        point.set(k, point.get(k) + dataGenIncrement);
                        carry = false;
                    }
                }
            }
        }

        System.out.println("Finishing data generation");

        return examples;
    }

    public static void save(String filename) {
        File fileToSave = null;

        if (filename.isEmpty()) {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Choose a file to save the weights to.");
            fileChooser.getExtensionFilters().add(new ExtensionFilter("Weights", "*.w8"));
            fileToSave = fileChooser.showSaveDialog(primaryStage);
        } else {
            fileToSave = new File(filename);
        }

        if (fileToSave == null) return;

        try {
            final PrintWriter writer = new PrintWriter(fileToSave);

            /* Save to file in the following format
            l:
                n: w1, w2, w3, w4, w5
                n: w1, w2, w3, w4, w5
            l:
                n: w1, w2, w3, w4, w5
                n: w1, w2, w3, w4, w5
             */

            network.layers.forEach(layer -> {
                        writer.print("l: ");
                        layer.nodes.forEach(node -> {
                                    writer.print("\tn: ");
                                    node.weights.stream()
                                            .map(weight -> weight + ", ")
                                            .forEach(writer::print);
                                    writer.println();
                                });
                        writer.println();
                    });
            writer.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private static boolean printHelp() {
        // Prints as git table
        // Prints headers
        System.out.format("| %-" + CommandLineParameter.flagLength + "s | " +
                        "%-" + CommandLineParameter.descriptionLength + "s | " +
                        "%-" + CommandLineParameter.defaultLength + "s | " +
                        "%-" + CommandLineParameter.parameterLength + "s |\n",
                "Flag", "Description", "Default", "Parameter");

        // Prints the lines below the header
        System.out.println(String.format("| %-" + CommandLineParameter.flagLength + "s | " +
                "%-" + CommandLineParameter.descriptionLength + "s |:" +
                "%-" + CommandLineParameter.defaultLength + "s:|:" +
                "%-" + CommandLineParameter.parameterLength + "s:|", "", "", "", "")
                .replaceAll(" ", "-"));

        Stream.of(commands).forEach(System.out::println);
        System.exit(0);
        return true;
    }

    private static boolean parseHiddenLayers(String arg) {
        hiddenLayers = new ArrayList<Integer>();
        for (String s : arg.split(",")) {
            hiddenLayers.add(Integer.parseInt(s.trim()));
        }
        return true;
    }

    private static CommandLineParameter[] commands = {
            new CommandLineParameter("-nogui", "Runs the application without a GUI",                           f -> useGUI = false,                    true, CommandLineParameter.Type.Void),     // No GUI
            new CommandLineParameter("-h",     "Displays the help text",                                       f -> printHelp(),                       null, CommandLineParameter.Type.Void),     // Help
            new CommandLineParameter("-rb",    "Sets the network to use radial basis",                         f -> isRadialBasis = true,             false, CommandLineParameter.Type.Void),     // Radial Basis
            new CommandLineParameter("-ds",    "The start point for the data (example) generation",            i -> dataGenStart = (double) i,           0d, CommandLineParameter.Type.Double),   // Data Generation Start
            new CommandLineParameter("-de",    "The end point for the data (example) generation",              i -> dataGenEnd = (double) i,             2d, CommandLineParameter.Type.Double),   // Data Generation End
            new CommandLineParameter("-di",    "The incrementation of the data point",                         i -> dataGenIncrement = (double) i,      .1d, CommandLineParameter.Type.Double),   // Data Generation Incrementation
            new CommandLineParameter("-hl",    "The amount of hidden layers, and the amount of nodes in each", s -> parseHiddenLayers((String) s),  "40,40", CommandLineParameter.Type.String),   // Hidden Layers
            new CommandLineParameter("-d",     "The number of dimensions the function will use",               i -> dimension = (int) i,                  2, CommandLineParameter.Type.Integer),  // Dimensions
            new CommandLineParameter("-s",     "Save the weights to a given output file",                      s -> savePath = (String) s,               "", CommandLineParameter.Type.String),   // Save

    };

    public static void main(String[] args) {
        try {
            // Init default values
            Stream.of(commands)
                    .parallel()
                    .filter(command -> command.paramType != CommandLineParameter.Type.Void) // Don't adjust types without params
                    .forEach(command -> command.func.apply(command.defaultValue));

            // Read command flags and use them
            for (int i = 0; i < args.length; i++) {
                for (CommandLineParameter command : commands) {
                    if (args[i].equals(command.flag)) {
                        switch (command.paramType) {
                            case Integer: command.func.apply(Integer.parseInt(args[++i])); break;
                            case Double: command.func.apply(Double.parseDouble(args[++i])); break;
                            case String: command.func.apply(args[++i]); break;
                            case Void: command.func.apply(0); break;
                        }
                    }
                }
            }
        } catch (NumberFormatException nfe) {
            System.err.println("Invalid arguments");
            System.exit(1);
        }

        if (useGUI) {
            launch(args);
        } else {
            start(dataGenStart, dataGenEnd, dataGenIncrement, hiddenLayers, dimension, isRadialBasis);
            if (!savePath.isEmpty()) save(savePath);
            System.exit(0);
        }

    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("format.fxml"));
        primaryStage.setScene(new Scene(loader.load()));
        controller = loader.getController();
        primaryStage.titleProperty().set("Rosenbrock Function Approximator");
        primaryStage.setResizable(false);
        primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("rosenbrock.jpg")));
        primaryStage.show();
        Main.primaryStage = primaryStage;
    }

    @Override
    public void stop() throws Exception {
        shouldStop = true;
        if (networkRun != null)
            networkRun.stop();
        super.stop();
    }

    /**
     * Class that represents a command line parameter
     */
    private static class CommandLineParameter {

        public static int flagLength = 6;
        public static int descriptionLength = 60;
        public static int defaultLength = 7;
        public static int parameterLength = 9;
        public Type paramType;
        private String flag;
        private String helpText;
        private Object defaultValue;
        private Function func;

        public CommandLineParameter(String flag, String helpText, Function func, Object defaultValue, Type paramType) {
            this.flag = flag;
            this.helpText = helpText;
            this.func = func;
            this.defaultValue = defaultValue;
            this.paramType = paramType;
        }

        private String toTable(String startFormat, String endFormat) {
            String formatMiddle = "";

            switch (paramType) {
                case Integer: formatMiddle = "%-" + defaultLength + "d"; break;
                case Double: formatMiddle = "%.3f" + String.format("%" + (defaultLength - 4 - ("" + (int) Math.floor((double) defaultValue)).length()) + "s", ""); break;
                case String: formatMiddle = "%-" + defaultLength + "s"; break;
                case Void: formatMiddle = "%-" + defaultLength + "s"; break;
            }

            return String.format(startFormat + formatMiddle + endFormat, flag, helpText, defaultValue == null ? "" : defaultValue, paramType);
        }

        @Override
        public String toString() {
            // return toTable("%-9s%-52s", "%-12s"); Alternate option
            return toTable("| %-" + flagLength + "s | %-" + descriptionLength + "s | ", " | %-" + parameterLength + "s |");
        }

        public enum Type {
            Integer,
            Double,
            String,
            Void
        }

    }

}
