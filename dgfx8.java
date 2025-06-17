import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.collections.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.paint.Color;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.binding.Bindings;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class dgfx8 extends Application {

    // Oracle Database Connection
    private Connection connection = null;
    private BooleanProperty isConnected = new SimpleBooleanProperty(false);

    // In-memory schema storage (will be populated from DB metadata or on table creation)
    private Map<String, Map<String, String>> tableSchemas = new HashMap<>();

    // UI elements for dynamic content
    private BorderPane rootLayout;
    private StackPane contentPane; // To switch between different forms/table views
    private Label messageLabel; // For displaying status and error messages

    // Predefined data types for application mapping (JDBC will handle actual SQL types)
    private static final String[] DATA_TYPES = {"VARCHAR(255)", "NUMBER", "DOUBLE PRECISION", "CHAR(1)"}; // Oracle types

    // UI elements for connection form
    private TextField dbUrlField;
    private TextField dbUserField;
    private PasswordField dbPasswordField;

    @Override
    public void start(Stage primaryStage) {
        rootLayout = new BorderPane();
        rootLayout.setPadding(new Insets(10));
        rootLayout.getStyleClass().add("root-layout"); // CSS class

        // --- Left Pane: Operation Buttons ---
        VBox operationButtons = new VBox(10);
        operationButtons.setPadding(new Insets(15));
        operationButtons.setAlignment(Pos.TOP_LEFT);
        operationButtons.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #cccccc; -fx-border-width: 0 1 0 0;");

        String[] operations = {"CREATE TABLE", "INSERT RECORD", "DELETE RECORD", "DROP TABLE", "SELECT TABLE", "TRUNCATE TABLE"};
        for (String op : operations) {
            Button btn = new Button(op);
            btn.setMaxWidth(Double.MAX_VALUE); // Make buttons fill width
            btn.setOnAction(e -> handleOperation(op));
            btn.getStyleClass().add("operation-button"); // CSS class
            btn.disableProperty().bind(isConnected.not()); // Disable buttons if not connected
            operationButtons.getChildren().add(btn);
        }
        rootLayout.setLeft(operationButtons);

        // --- Center Pane: Dynamic Content ---
        contentPane = new StackPane();
        contentPane.setPadding(new Insets(10));
        contentPane.setAlignment(Pos.TOP_CENTER);
        rootLayout.setCenter(contentPane);

        // --- Bottom Pane: Message Label ---
        messageLabel = new Label("Welcome to Oracle Database Client!");
        messageLabel.getStyleClass().add("message-label"); // CSS class
        HBox messageBox = new HBox(messageLabel);
        messageBox.setPadding(new Insets(5, 10, 5, 10));
        messageBox.setAlignment(Pos.CENTER);
        rootLayout.setBottom(messageBox);

        Scene scene = new Scene(rootLayout, 800, 600);
        scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm()); // Load CSS
        primaryStage.setScene(scene);
        primaryStage.setTitle("Enhanced Oracle Database Client");
        primaryStage.show();

        // Show connection form initially
        showConnectionForm();
    }

    private void showConnectionForm() {
        contentPane.getChildren().clear();
        showMessage("Enter Oracle database connection details.", false);

        VBox formContainer = new VBox(10);
        formContainer.setPadding(new Insets(20));
        formContainer.setAlignment(Pos.TOP_LEFT);
        formContainer.getStyleClass().add("form-panel");

        GridPane connectionGrid = new GridPane();
        connectionGrid.setHgap(10);
        connectionGrid.setVgap(10);
        connectionGrid.setAlignment(Pos.TOP_LEFT);

        Label urlLabel = new Label("DB URL:");
        dbUrlField = new TextField("jdbc:oracle:thin:@localhost:1521:XE"); // Example URL
        dbUrlField.setPromptText("jdbc:oracle:thin:@host:port:sid");

        Label userLabel = new Label("Username:");
        dbUserField = new TextField("system"); // Example username
        dbUserField.setPromptText("Enter username");

        Label passwordLabel = new Label("Password:");
        dbPasswordField = new PasswordField();
        dbPasswordField.setPromptText("Enter password");

        connectionGrid.addRow(0, urlLabel, dbUrlField);
        connectionGrid.addRow(1, userLabel, dbUserField);
        connectionGrid.addRow(2, passwordLabel, dbPasswordField);

        Button connectButton = new Button("Connect to Database");
        connectButton.getStyleClass().add("operation-button");
        connectButton.setOnAction(e -> connectToDatabase());

        Button disconnectButton = new Button("Disconnect");
        disconnectButton.getStyleClass().add("operation-button");
        disconnectButton.disableProperty().bind(isConnected.not());
        disconnectButton.setOnAction(e -> disconnectFromDatabase());


        HBox connectionButtons = new HBox(10, connectButton, disconnectButton);
        connectionButtons.setAlignment(Pos.CENTER_LEFT);

        formContainer.getChildren().addAll(new Label("Database Connection"), connectionGrid, connectionButtons);
        contentPane.getChildren().add(formContainer);
    }

    private void connectToDatabase() {
        String url = dbUrlField.getText().trim();
        String user = dbUserField.getText().trim();
        String password = dbPasswordField.getText().trim();

        if (url.isEmpty() || user.isEmpty() || password.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Connection Error", "Please fill in all connection details.");
            return;
        }

        try {
            // Load Oracle JDBC driver (not strictly necessary for newer JDBC versions but good practice)
            Class.forName("oracle.jdbc.driver.OracleDriver");
            connection = DriverManager.getConnection(url, user, password);
            isConnected.set(true);
            showMessage("Successfully connected to Oracle database!", false);
            contentPane.getChildren().clear(); // Clear connection form
            // Optionally, show a default view or first table
            if (!getAllTableNames().isEmpty()) {
                showSelectTableForm();
            } else {
                showMessage("Connected. No tables found. Create a new table.", false);
            }
        } catch (ClassNotFoundException e) {
            showAlert(Alert.AlertType.ERROR, "JDBC Driver Error", "Oracle JDBC Driver not found. Make sure ojdbc.jar is in your classpath. " + e.getMessage());
            isConnected.set(false);
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Database Connection Error", "Failed to connect to database: " + e.getMessage());
            isConnected.set(false);
        }
    }

    private void disconnectFromDatabase() {
        if (connection != null) {
            try {
                connection.close();
                isConnected.set(false);
                showMessage("Disconnected from database.", false);
                contentPane.getChildren().clear();
                showConnectionForm(); // Show connection form again
            } catch (SQLException e) {
                showAlert(Alert.AlertType.ERROR, "Disconnection Error", "Failed to disconnect: " + e.getMessage());
            }
        }
    }


    private void handleOperation(String operation) {
        if (!isConnected.get()) {
            showAlert(Alert.AlertType.WARNING, "Not Connected", "Please connect to the database first.");
            showConnectionForm(); // Show connection form if not connected
            return;
        }

        contentPane.getChildren().clear(); // Clear previous content
        showMessage("", false); // Clear previous messages

        switch (operation) {
            case "CREATE TABLE":
                showCreateTableForm();
                break;
            case "INSERT RECORD":
                showInsertRecordForm();
                break;
            case "DELETE RECORD":
                showDeleteRecordForm();
                break;
            case "DROP TABLE":
                showDropTableForm();
                break;
            case "SELECT TABLE":
                showSelectTableForm();
                break;
            case "TRUNCATE TABLE":
                showTruncateTableForm();
                break;
        }
    }

    // --- UI Forms and Logic for Operations ---

    private void showCreateTableForm() {
        VBox formContainer = new VBox(10);
        formContainer.setPadding(new Insets(20));
        formContainer.setAlignment(Pos.TOP_LEFT);
        formContainer.getStyleClass().add("form-panel");

        GridPane tableInfoGrid = new GridPane();
        tableInfoGrid.setHgap(10);
        tableInfoGrid.setVgap(10);
        tableInfoGrid.setAlignment(Pos.TOP_LEFT);

        Label tableNameLabel = new Label("Table Name:");
        TextField tableNameField = new TextField();
        tableNameField.setPromptText("Enter table name");
        tableInfoGrid.addRow(0, tableNameLabel, tableNameField);
        formContainer.getChildren().add(tableInfoGrid);

        Label fieldsHeader = new Label("Define Fields:");
        fieldsHeader.setStyle("-fx-font-weight: bold; -fx-padding: 10 0 5 0;");
        formContainer.getChildren().add(fieldsHeader);

        VBox fieldsVBox = new VBox(5);
        fieldsVBox.setPadding(new Insets(0, 0, 10, 0));
        formContainer.getChildren().add(fieldsVBox);

        Button addFieldButton = new Button("Add Field");
        addFieldButton.setOnAction(e -> addFieldRow(fieldsVBox));
        formContainer.getChildren().add(addFieldButton);

        Button createButton = new Button("Create Table");
        createButton.setOnAction(e -> {
            String tableName = tableNameField.getText().trim().toUpperCase(); // Oracle table names often uppercase
            Map<String, String> fieldsSchema = new LinkedHashMap<>(); // Use LinkedHashMap to preserve order

            // --- Pre-validation for table name and overall fields list ---
            if (tableName.isEmpty()) {
                showAlert(Alert.AlertType.ERROR, "Input Error", "Table name cannot be empty.");
                return; // Abort if table name is empty
            }

            // Check if table already exists in DB
            if (getAllTableNames().contains(tableName)) {
                showAlert(Alert.AlertType.ERROR, "Error", "Table '" + tableName + "' already exists in the database.");
                return;
            }

            if (fieldsVBox.getChildren().isEmpty()) {
                showAlert(Alert.AlertType.ERROR, "Input Error", "Table must have at least one field. Use 'Add Field' button.");
                return; // Abort if no fields are defined
            }
            // --- End Pre-validation ---


            // --- Iterate through dynamic field rows for individual field validation ---
            for (int i = 0; i < fieldsVBox.getChildren().size(); i++) {
                HBox row = (HBox) fieldsVBox.getChildren().get(i);
                TextField nameField = (TextField) row.getChildren().get(1); // Assuming index 1 for TextField
                ChoiceBox<String> typeChoiceBox = (ChoiceBox<String>) row.getChildren().get(3); // Assuming index 3 for ChoiceBox (after Name Label, TextField, Type Label)

                String fieldName = nameField.getText().trim().toUpperCase(); // Oracle column names often uppercase
                String fieldType = typeChoiceBox.getValue();

                if (fieldName.isEmpty()) { // Field name cannot be empty
                    showAlert(Alert.AlertType.ERROR, "Input Error", "Field name cannot be empty for field " + (i + 1) + ".");
                    return; // Abort if field name is empty
                }
                if (fieldType == null) { // Field type must be selected
                    showAlert(Alert.AlertType.ERROR, "Input Error", "Please select a data type for field '" + fieldName + "'.");
                    return; // Abort if field type is not selected
                }
                if (fieldsSchema.containsKey(fieldName)) { // Duplicate field name check
                    showAlert(Alert.AlertType.ERROR, "Input Error", "Duplicate field name: '" + fieldName + "'. Field names must be unique.");
                    return; // Abort if duplicate field name
                }
                fieldsSchema.put(fieldName, fieldType);
            }
            // If all validations pass in this action listener, then call createTable
            createTable(tableName, fieldsSchema);
        });
        formContainer.getChildren().add(createButton);

        // Add an initial field row when the form is shown
        addFieldRow(fieldsVBox);

        contentPane.getChildren().add(formContainer);
    }

    private void addFieldRow(VBox fieldsVBox) {
        HBox fieldRow = new HBox(5);
        fieldRow.setAlignment(Pos.CENTER_LEFT);
        fieldRow.setPadding(new Insets(2, 0, 2, 0));

        Label nameLabel = new Label("Name:");
        TextField fieldNameField = new TextField();
        fieldNameField.setPromptText("Field Name");
        fieldNameField.setPrefWidth(120);

        Label typeLabel = new Label("Type:");
        ChoiceBox<String> dataTypeChoiceBox = new ChoiceBox<>(FXCollections.observableArrayList(DATA_TYPES));
        dataTypeChoiceBox.getSelectionModel().selectFirst(); // Select the first type by default

        Button removeButton = new Button("X");
        removeButton.setStyle("-fx-background-color: #ff6666; -fx-text-fill: white; -fx-font-weight: bold;");
        removeButton.setOnAction(e -> fieldsVBox.getChildren().remove(fieldRow));

        // Order of children: Label, TextField, Label, ChoiceBox, Button
        fieldRow.getChildren().addAll(nameLabel, fieldNameField, typeLabel, dataTypeChoiceBox, removeButton);
        fieldsVBox.getChildren().add(fieldRow);
    }

    private void createTable(String tableName, Map<String, String> schema) {
        if (connection == null) {
            showAlert(Alert.AlertType.ERROR, "Database Error", "No active database connection.");
            return;
        }

        StringBuilder sql = new StringBuilder("CREATE TABLE ");
        sql.append(tableName).append(" (");

        boolean firstField = true;
        for (Map.Entry<String, String> entry : schema.entrySet()) {
            if (!firstField) {
                sql.append(", ");
            }
            sql.append(entry.getKey()).append(" ").append(entry.getValue());
            firstField = false;
        }
        sql.append(")");

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql.toString());
            // Store the schema after successful creation in the actual database
            tableSchemas.put(tableName, schema);
            showMessage("Table '" + tableName + "' created successfully in Oracle.", false);
            contentPane.getChildren().clear(); // Clear the form after success
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "SQL Error", "Failed to create table '" + tableName + "': " + e.getMessage());
        }
    }

    private void showInsertRecordForm() {
        List<String> currentTables = getAllTableNames();
        if (currentTables.isEmpty()) {
            showMessage("No tables available in the database. Please create a table first.", true);
            return;
        }

        VBox formContainer = new VBox(10);
        formContainer.setPadding(new Insets(20));
        formContainer.setAlignment(Pos.TOP_LEFT);
        formContainer.getStyleClass().add("form-panel");

        Label selectTableLabel = new Label("Select Table:");
        ChoiceBox<String> tableChoiceBox = new ChoiceBox<>(FXCollections.observableArrayList(currentTables));
        tableChoiceBox.setPrefWidth(200);
        formContainer.getChildren().addAll(selectTableLabel, tableChoiceBox);

        Label recordsHeader = new Label("Records to Insert:");
        recordsHeader.setStyle("-fx-font-weight: bold; -fx-padding: 10 0 5 0;");
        formContainer.getChildren().add(recordsHeader);

        // This VBox will hold all the dynamically added record input rows
        VBox recordsInputVBox = new VBox(15); // Increased spacing for better separation
        recordsInputVBox.setPadding(new Insets(10, 0, 10, 0));
        formContainer.getChildren().add(new ScrollPane(recordsInputVBox) {{
            setFitToWidth(true);
            setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            setPrefHeight(250); // Set a preferred height for the scrollable area
            setStyle("-fx-background-color: transparent; -fx-border-color: #e0e0e0; -fx-border-radius: 5;");
        }});


        Button addRecordRowButton = new Button("Add Record Row");
        // addRecordRowButton.disableProperty().bind(tableChoiceBox.valueProperty().isNull()); // Re-enable for Oracle
        addRecordRowButton.disableProperty().bind(tableChoiceBox.valueProperty().isNull());
        addRecordRowButton.getStyleClass().add("add-record-button"); // Custom CSS

        Button insertAllButton = new Button("Insert All Records");
        // Fix: Use Bindings.isEmpty() for ObservableList binding
        insertAllButton.disableProperty().bind(tableChoiceBox.valueProperty().isNull().or(Bindings.isEmpty(recordsInputVBox.getChildren())));
        insertAllButton.getStyleClass().add("insert-all-button"); // Custom CSS

        HBox controlButtons = new HBox(10, addRecordRowButton, insertAllButton);
        controlButtons.setAlignment(Pos.CENTER_LEFT);
        formContainer.getChildren().add(controlButtons);

        // List to store the TextField lists for each row (each record)
        List<List<TextField>> allRecordTextFields = new ArrayList<>();

        tableChoiceBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newTableName) -> {
            recordsInputVBox.getChildren().clear(); // Clear existing record rows
            allRecordTextFields.clear(); // Clear stored text field references

            if (newTableName != null) {
                // Fetch schema from DB for the selected table
                Map<String, String> schema = getSchemaFromDatabase(newTableName);
                tableSchemas.put(newTableName, schema); // Cache schema for insertion

                if (schema != null && !schema.isEmpty()) {
                    addRecordRow(recordsInputVBox, schema, allRecordTextFields);
                } else {
                    showMessage("Could not retrieve schema for table '" + newTableName + "'. Cannot insert.", true);
                }
            }
        });

        // Action for "Add Record Row" button
        addRecordRowButton.setOnAction(e -> {
            String selectedTable = tableChoiceBox.getValue();
            if (selectedTable != null) {
                Map<String, String> schema = tableSchemas.get(selectedTable); // Use cached schema
                if (schema == null) { // Fallback if schema not cached (shouldn't happen with listener)
                    schema = getSchemaFromDatabase(selectedTable);
                    tableSchemas.put(selectedTable, schema);
                }
                if (schema != null && !schema.isEmpty()) {
                    addRecordRow(recordsInputVBox, schema, allRecordTextFields);
                } else {
                    showMessage("Could not retrieve schema for table '" + selectedTable + "'. Cannot add record row.", true);
                }
            }
        });

        // Action for "Insert All Records" button
        insertAllButton.setOnAction(e -> {
            String selectedTable = tableChoiceBox.getValue();
            if (selectedTable != null) {
                insertMultipleRecords(selectedTable, tableSchemas.get(selectedTable), allRecordTextFields);
            }
        });

        contentPane.getChildren().add(formContainer);
    }

    private void addRecordRow(VBox recordsInputVBox, Map<String, String> schema, List<List<TextField>> allRecordTextFields) {
        VBox recordRowContainer = new VBox(5);
        recordRowContainer.setPadding(new Insets(10));
        recordRowContainer.setStyle("-fx-border-color: #d0d0d0; -fx-border-width: 1; -fx-border-radius: 5; -fx-background-color: #f9f9f9;");
        recordRowContainer.getStyleClass().add("record-row");

        List<TextField> currentRowTextFields = new ArrayList<>();
        GridPane fieldsGrid = new GridPane();
        fieldsGrid.setHgap(10);
        fieldsGrid.setVgap(5);
        fieldsGrid.setAlignment(Pos.TOP_LEFT);

        int rowIdx = 0;
        for (Map.Entry<String, String> entry : schema.entrySet()) {
            String fieldName = entry.getKey();
            String fieldType = entry.getValue(); // This will be the Oracle SQL type, e.g., VARCHAR(255)

            Label label = new Label(fieldName + " (" + fieldType + "):");
            TextField valueField = new TextField();
            // Use the application-level type for prompt, e.g., VARCHAR instead of VARCHAR(255)
            String promptType = fieldType.split("\\(")[0].trim();
            valueField.setPromptText("Enter " + fieldName + " (" + promptType + ")");
            valueField.setPrefWidth(180); // Consistent width

            fieldsGrid.addRow(rowIdx++, label, valueField);
            currentRowTextFields.add(valueField);
        }

        Button removeRowButton = new Button("Remove Record Row");
        removeRowButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");
        removeRowButton.setOnAction(e -> {
            recordsInputVBox.getChildren().remove(recordRowContainer);
            allRecordTextFields.remove(currentRowTextFields); // Remove references
            // No need to update hasRecordRows property as it's bound to recordsInputVBox.getChildren().emptyProperty()
        });

        recordRowContainer.getChildren().addAll(fieldsGrid, removeRowButton);
        recordsInputVBox.getChildren().add(recordRowContainer);
        allRecordTextFields.add(currentRowTextFields); // Add the list of text fields for this new record
    }

    private void insertMultipleRecords(String tableName, Map<String, String> schema, List<List<TextField>> allRecordTextFields) {
        if (connection == null) {
            showAlert(Alert.AlertType.ERROR, "Database Error", "No active database connection.");
            return;
        }
        if (allRecordTextFields.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Records", "No record rows to insert. Add at least one record row.");
            return;
        }

        // Prepare the INSERT statement
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(tableName).append(" (");
        StringBuilder valuesPlaceholders = new StringBuilder(" VALUES (");
        boolean firstField = true;
        for (String fieldName : schema.keySet()) {
            if (!firstField) {
                sql.append(", ");
                valuesPlaceholders.append(", ");
            }
            sql.append(fieldName);
            valuesPlaceholders.append("?");
            firstField = false;
        }
        sql.append(")");
        valuesPlaceholders.append(")");
        sql.append(valuesPlaceholders);

        int successfulInserts = 0;
        int failedInserts = 0;
        StringBuilder errorMessages = new StringBuilder();

        try (PreparedStatement pstmt = connection.prepareStatement(sql.toString())) {
            for (int recordIndex = 0; recordIndex < allRecordTextFields.size(); recordIndex++) {
                List<TextField> recordTextFields = allRecordTextFields.get(recordIndex);
                boolean rowHasError = false;
                int fieldParamIndex = 1; // JDBC parameters are 1-indexed

                int fieldListIndex = 0;
                String currentBaseType = ""; // Initialize baseType here to ensure it's always in scope
                for (Map.Entry<String, String> entry : schema.entrySet()) {
                    String fieldName = entry.getKey();
                    String fieldType = entry.getValue(); // Oracle SQL type (e.g., VARCHAR2, NUMBER)
                    String inputValue = recordTextFields.get(fieldListIndex).getText().trim();

                    try {
                        // Determine the base type for conversion (e.g., VARCHAR from VARCHAR(255))
                        currentBaseType = fieldType.split("\\(")[0].trim(); // Assign to the initialized variable
                        Object convertedValue = convertType(inputValue, currentBaseType);

                        if (convertedValue == null) {
                            pstmt.setNull(fieldParamIndex, getSqlType(currentBaseType));
                        } else {
                            // Oracle specific type handling
                            if (currentBaseType.equalsIgnoreCase("VARCHAR")) {
                                pstmt.setString(fieldParamIndex, (String) convertedValue);
                            } else if (currentBaseType.equalsIgnoreCase("NUMBER") || currentBaseType.equalsIgnoreCase("INT")) {
                                pstmt.setInt(fieldParamIndex, (Integer) convertedValue);
                            } else if (currentBaseType.equalsIgnoreCase("DOUBLE") || currentBaseType.equalsIgnoreCase("DOUBLE PRECISION")) {
                                pstmt.setDouble(fieldParamIndex, (Double) convertedValue);
                            } else if (currentBaseType.equalsIgnoreCase("CHAR")) { // For BOOLEAN represented as CHAR(1)
                                pstmt.setString(fieldParamIndex, (String) convertedValue);
                            } else {
                                // Default or more specific handling if needed
                                pstmt.setObject(fieldParamIndex, convertedValue);
                            }
                        }
                    } catch (NumberFormatException ex) {
                        errorMessages.append("Record ").append(recordIndex + 1).append(", Field '").append(fieldName).append("': Invalid number format. Expected ").append(currentBaseType).append(".\n"); // Use currentBaseType
                        rowHasError = true;
                        break;
                    } catch (IllegalArgumentException ex) {
                        errorMessages.append("Record ").append(recordIndex + 1).append(", Field '").append(fieldName)
                                .append("': ").append(ex.getMessage()).append(".\n");
                        rowHasError = true;
                        break;
                    }
                    fieldParamIndex++;
                    fieldListIndex++;
                }

                if (!rowHasError) {
                    try {
                        pstmt.executeUpdate();
                        successfulInserts++;
                    } catch (SQLException ex) {
                        errorMessages.append("Record ").append(recordIndex + 1).append(": Database error during insertion: ").append(ex.getMessage()).append("\n");
                        failedInserts++;
                    }
                } else {
                    failedInserts++;
                }
            }
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "SQL Preparation Error", "Failed to prepare insert statement: " + e.getMessage());
            return;
        }

        if (successfulInserts > 0) {
            showMessage(successfulInserts + " record(s) inserted into '" + tableName + "' successfully.", false);
        }
        if (failedInserts > 0) {
            showAlert(Alert.AlertType.WARNING, "Insertion Errors",
                    failedInserts + " record(s) failed to insert due to:\n" + errorMessages.toString());
            showMessage("Partial success! " + successfulInserts + " records inserted, " + failedInserts + " failed.", true);
        }

        contentPane.getChildren().clear();
        displayTable(tableName); // Refresh view after insertion attempts
    }

    // Helper to get JDBC SQL Type for setNull
    private int getSqlType(String baseType) {
        switch (baseType.toUpperCase()) {
            case "VARCHAR": return Types.VARCHAR;
            case "NUMBER":
            case "INT": return Types.NUMERIC;
            case "DOUBLE":
            case "DOUBLE PRECISION": return Types.DOUBLE;
            case "CHAR": return Types.CHAR; // For boolean
            default: return Types.OTHER;
        }
    }


    private void showDeleteRecordForm() {
        List<String> currentTables = getAllTableNames();
        if (currentTables.isEmpty()) {
            showMessage("No tables available in the database to delete records from.", true);
            return;
        }

        VBox formContainer = new VBox(10);
        formContainer.setPadding(new Insets(20));
        formContainer.setAlignment(Pos.TOP_LEFT);
        formContainer.getStyleClass().add("form-panel");

        Label selectTableLabel = new Label("Select Table:");
        ChoiceBox<String> tableChoiceBox = new ChoiceBox<>(FXCollections.observableArrayList(currentTables));
        tableChoiceBox.setPrefWidth(200);
        formContainer.getChildren().addAll(selectTableLabel, tableChoiceBox);

        // Toggle Group for delete options
        ToggleGroup deleteOptionToggle = new ToggleGroup();
        RadioButton conditionRadio = new RadioButton("Delete by Condition");
        conditionRadio.setToggleGroup(deleteOptionToggle);
        RadioButton checkboxRadio = new RadioButton("Delete by Checkbox");
        checkboxRadio.setToggleGroup(deleteOptionToggle);

        HBox radioButtons = new HBox(15, conditionRadio, checkboxRadio);
        radioButtons.setPadding(new Insets(10, 0, 10, 0));
        formContainer.getChildren().add(radioButtons);

        // Containers for dynamic content based on selection
        VBox conditionPanel = new VBox(10);
        conditionPanel.setPadding(new Insets(10));
        conditionPanel.setStyle("-fx-border-color: #d0d0d0; -fx-border-width: 1; -fx-border-radius: 5;");
        conditionPanel.managedProperty().bind(conditionRadio.selectedProperty());
        conditionPanel.visibleProperty().bind(conditionRadio.selectedProperty());

        GridPane conditionGrid = new GridPane();
        conditionGrid.setHgap(10);
        conditionGrid.setVgap(10);
        Label criteriaLabel = new Label("Deletion Criteria (Field=Value):");
        TextField criteriaField = new TextField();
        criteriaField.setPromptText("e.g., ID=101 or NAME='John Doe' or ACTIVE='T'");
        conditionGrid.addRow(0, criteriaLabel, criteriaField);
        Button deleteConditionButton = new Button("Delete by Condition");
        deleteConditionButton.disableProperty().bind(tableChoiceBox.valueProperty().isNull());
        conditionGrid.add(deleteConditionButton, 1, 1);
        conditionPanel.getChildren().add(conditionGrid);

        VBox checkboxPanel = new VBox(10);
        checkboxPanel.setPadding(new Insets(10));
        checkboxPanel.setStyle("-fx-border-color: #d0d0d0; -fx-border-width: 1; -fx-border-radius: 5;");
        checkboxPanel.managedProperty().bind(checkboxRadio.selectedProperty());
        checkboxPanel.visibleProperty().bind(checkboxRadio.selectedProperty());

        Button deleteSelectedButton = new Button("Delete Selected Records");
        deleteSelectedButton.disableProperty().bind(tableChoiceBox.valueProperty().isNull());
        TableView<Map<String, Object>> checkboxTableView = new TableView<>(); // Will be populated dynamically
        checkboxTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        checkboxTableView.setPlaceholder(new Label("Select a table to load records for checkbox deletion."));

        checkboxPanel.getChildren().addAll(new Label("Select records to delete:"), checkboxTableView, deleteSelectedButton);

        formContainer.getChildren().addAll(conditionPanel, checkboxPanel);

        // Listeners
        tableChoiceBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newTableName) -> {
            if (newTableName != null) {
                // Pre-populate schema for consistency
                Map<String, String> schema = getSchemaFromDatabase(newTableName);
                tableSchemas.put(newTableName, schema);

                if (checkboxRadio.isSelected()) {
                    populateCheckboxTableView(newTableName, checkboxTableView);
                }
                deleteConditionButton.setOnAction(e -> deleteRecordByCondition(newTableName, criteriaField.getText().trim()));
                deleteSelectedButton.setOnAction(e -> deleteRecordByCheckbox(newTableName, checkboxTableView));
            } else {
                checkboxTableView.getColumns().clear();
                checkboxTableView.getItems().clear();
            }
        });

        deleteOptionToggle.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (tableChoiceBox.getValue() != null) {
                if (newVal == checkboxRadio) {
                    populateCheckboxTableView(tableChoiceBox.getValue(), checkboxTableView);
                } else {
                    checkboxTableView.getColumns().clear();
                    checkboxTableView.getItems().clear();
                }
            }
        });

        // Set initial selection
        conditionRadio.setSelected(true);
        contentPane.getChildren().add(formContainer);
    }

    private void populateCheckboxTableView(String tableName, TableView<Map<String, Object>> tableView) {
        if (connection == null) {
            showAlert(Alert.AlertType.ERROR, "Database Error", "No active database connection.");
            return;
        }

        tableView.getColumns().clear();
        tableView.getItems().clear();

        ObservableList<Map<String, Object>> tableData = FXCollections.observableArrayList();

        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM " + tableName)) {

            ResultSetMetaData rsmd = rs.getMetaData();
            int columnCount = rsmd.getColumnCount();

            // Add checkbox column
            TableColumn<Map<String, Object>, Boolean> selectColumn = new TableColumn<>("Select");
            selectColumn.setCellValueFactory(param -> {
                // Each row needs a BooleanProperty for its checkbox state
                // Store this BooleanProperty within the row map itself or a separate tracking map
                // For simplicity here, let's assume we add a "selected" property to each map
                Map<String, Object> row = param.getValue();
                if (!row.containsKey("__SELECTED__")) {
                    row.put("__SELECTED__", new SimpleBooleanProperty(false));
                }
                return (BooleanProperty) row.get("__SELECTED__");
            });
            selectColumn.setCellFactory(tc -> new TableCell<Map<String, Object>, Boolean>() {
                private final CheckBox checkBox = new CheckBox();
                {
                    checkBox.setOnAction(event -> {
                        Map<String, Object> row = (Map<String, Object>) getTableRow().getItem();
                        if (row != null) {
                            ((SimpleBooleanProperty) row.get("__SELECTED__")).set(checkBox.isSelected());
                        }
                    });
                }
                @Override
                protected void updateItem(Boolean item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) {
                        setGraphic(null);
                    } else {
                        checkBox.setSelected(item != null && item);
                        setGraphic(checkBox);
                    }
                }
            });
            selectColumn.setPrefWidth(50);
            selectColumn.setMinWidth(50);
            selectColumn.setResizable(false);
            tableView.getColumns().add(selectColumn);

            // Add data columns
            for (int i = 1; i <= columnCount; i++) {
                String columnName = rsmd.getColumnName(i);
                TableColumn<Map<String, Object>, String> column = new TableColumn<>(columnName);
                column.setCellValueFactory(cellData -> {
                    Object value = cellData.getValue().get(columnName);
                    return new SimpleStringProperty(value != null ? value.toString() : "NULL");
                });
                tableView.getColumns().add(column);
            }

            // Populate table data
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = rsmd.getColumnName(i);
                    Object value = rs.getObject(i);
                    row.put(columnName, value);
                }
                // Initialize the selection property for each row
                row.put("__SELECTED__", new SimpleBooleanProperty(false));
                tableData.add(row);
            }
            tableView.setItems(tableData);

        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "SQL Error", "Failed to retrieve table data for checkbox deletion from '" + tableName + "': " + e.getMessage());
        }
    }


    private void deleteRecordByCheckbox(String tableName, TableView<Map<String, Object>> tableView) {
        if (connection == null) {
            showAlert(Alert.AlertType.ERROR, "Database Error", "No active database connection.");
            return;
        }

        ObservableList<Map<String, Object>> selectedRecords = tableView.getItems().stream()
                .filter(row -> ((SimpleBooleanProperty) row.get("__SELECTED__")).get())
                .collect(Collectors.toCollection(FXCollections::observableArrayList));

        if (selectedRecords.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "No records selected for deletion. Please select at least one record.");
            return;
        }

        Map<String, String> schema = getSchemaFromDatabase(tableName);
        if (schema == null || schema.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Schema Error", "Could not retrieve schema for table '" + tableName + "'. Cannot proceed with deletion.");
            return;
        }

        // Construct the WHERE clause using all columns of the selected row for uniqueness
        // This is a generic approach; for large tables or tables without proper keys, it might be inefficient.
        // A more robust solution would involve fetching actual primary key information.
        StringBuilder deleteSqlBuilder = new StringBuilder("DELETE FROM ").append(tableName).append(" WHERE ");
        List<String> columnNames = new ArrayList<>(schema.keySet()); // Get column names in order

        StringBuilder whereClauseParts = new StringBuilder();
        for (String colName : columnNames) {
            if (whereClauseParts.length() > 0) {
                whereClauseParts.append(" AND ");
            }
            whereClauseParts.append(colName).append(" = ?");
        }
        deleteSqlBuilder.append(whereClauseParts);
        String deleteSql = deleteSqlBuilder.toString();

        int successfulDeletions = 0;
        int failedDeletions = 0;
        StringBuilder errorMessages = new StringBuilder();

        try (PreparedStatement pstmt = connection.prepareStatement(deleteSql)) {
            for (Map<String, Object> record : selectedRecords) {
                int paramIndex = 1;
                boolean currentRecordHasError = false;
                for (String columnName : columnNames) {
                    String columnType = schema.get(columnName);
                    Object value = record.get(columnName); // This value is already the Object from the ResultSet

                    try {
                        String baseType = columnType.split("\\(")[0].trim();
                        if (value == null) {
                            pstmt.setNull(paramIndex, getSqlType(baseType));
                        } else {
                            // Convert the object back to its appropriate JDBC type for PreparedStatement
                            if (baseType.equalsIgnoreCase("VARCHAR")) {
                                pstmt.setString(paramIndex, value.toString());
                            } else if (baseType.equalsIgnoreCase("NUMBER") || baseType.equalsIgnoreCase("INT")) {
                                pstmt.setInt(paramIndex, ((Number) value).intValue());
                            } else if (baseType.equalsIgnoreCase("DOUBLE") || baseType.equalsIgnoreCase("DOUBLE PRECISION")) {
                                pstmt.setDouble(paramIndex, ((Number) value).doubleValue());
                            } else if (baseType.equalsIgnoreCase("CHAR")) {
                                pstmt.setString(paramIndex, value.toString());
                            } else {
                                pstmt.setObject(paramIndex, value);
                            }
                        }
                    } catch (SQLException e) {
                        errorMessages.append("Failed to set parameter for column '").append(columnName)
                                .append("': ").append(e.getMessage()).append("\n");
                        currentRecordHasError = true;
                        break;
                    }
                    paramIndex++;
                }

                if (!currentRecordHasError) {
                    try {
                        int rowsAffected = pstmt.executeUpdate();
                        if (rowsAffected > 0) {
                            successfulDeletions++;
                        } else {
                            failedDeletions++;
                            errorMessages.append("No record deleted for selected row (possibly not found or duplicate in table).\n");
                        }
                    } catch (SQLException e) {
                        failedDeletions++;
                        errorMessages.append("Database error during deletion for a record: ").append(e.getMessage()).append("\n");
                    }
                } else {
                    failedDeletions++;
                }
            }
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "SQL Preparation Error", "Failed to prepare delete statement: " + e.getMessage());
            return;
        }

        if (successfulDeletions > 0) {
            showMessage(successfulDeletions + " record(s) deleted from '" + tableName + "' successfully.", false);
        }
        if (failedDeletions > 0) {
            showAlert(Alert.AlertType.WARNING, "Deletion Errors",
                    failedDeletions + " record(s) failed to delete due to:\n" + errorMessages.toString());
            showMessage("Partial success! " + successfulDeletions + " records deleted, " + failedDeletions + " failed.", true);
        }
        contentPane.getChildren().clear();
        displayTable(tableName); // Refresh view after deletion attempts
    }


    private void deleteRecordByCondition(String tableName, String criteria) {
        if (connection == null) {
            showAlert(Alert.AlertType.ERROR, "Database Error", "No active database connection.");
            return;
        }
        if (criteria.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Input Error", "Deletion criteria cannot be empty.");
            return;
        }

        String[] parts = criteria.split("=", 2); // Split only on the first '='
        if (parts.length != 2) {
            showAlert(Alert.AlertType.ERROR, "Input Error", "Invalid criteria format. Use 'FIELD=VALUE'.");
            return;
        }
        String fieldName = parts[0].trim().toUpperCase();
        String fieldValueStr = parts[1].trim();

        // Get schema from DB for type validation
        Map<String, String> schema = getSchemaFromDatabase(tableName);
        if (schema == null || !schema.containsKey(fieldName)) {
            showAlert(Alert.AlertType.ERROR, "Input Error", "Field '" + fieldName + "' does not exist in table '" + tableName + "'.");
            return;
        }

        // Determine the base type for conversion from Oracle SQL type (e.g., NUMBER from NUMBER(10,0))
        String fieldType = schema.get(fieldName);
        String baseType = fieldType.split("\\(")[0].trim();

        Object expectedValue;
        try {
            expectedValue = convertType(fieldValueStr, baseType);
        } catch (NumberFormatException ex) {
            showAlert(Alert.AlertType.ERROR, "Data Type Error", "Invalid number format for criteria value '" + fieldValueStr + "'. Expected " + baseType + ". Details: " + ex.getMessage());
            return;
        } catch (IllegalArgumentException ex) {
            showAlert(Alert.AlertType.ERROR, "Data Type Error", ex.getMessage() + " for criteria value '" + fieldValueStr + "'.");
            return;
        }

        String sql = "DELETE FROM " + tableName + " WHERE " + fieldName + " = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            if (expectedValue == null) {
                pstmt.setNull(1, getSqlType(baseType));
            } else {
                // Oracle specific type handling for setting parameter
                if (baseType.equalsIgnoreCase("VARCHAR")) {
                    pstmt.setString(1, (String) expectedValue);
                } else if (baseType.equalsIgnoreCase("NUMBER") || baseType.equalsIgnoreCase("INT")) {
                    pstmt.setInt(1, (Integer) expectedValue);
                } else if (baseType.equalsIgnoreCase("DOUBLE") || baseType.equalsIgnoreCase("DOUBLE PRECISION")) {
                    pstmt.setDouble(1, (Double) expectedValue);
                } else if (baseType.equalsIgnoreCase("CHAR")) { // For BOOLEAN represented as CHAR(1)
                    pstmt.setString(1, (String) expectedValue);
                } else {
                    pstmt.setObject(1, expectedValue);
                }
            }
            int deletedRows = pstmt.executeUpdate();
            if (deletedRows > 0) {
                showMessage(deletedRows + " record(s) deleted from '" + tableName + "' matching condition '" + criteria + "'.", false);
            } else {
                showMessage("No records found matching the condition '" + criteria + "' in table '" + tableName + "'.", false);
            }
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "SQL Error", "Failed to delete records: " + e.getMessage());
        }
        contentPane.getChildren().clear();
        displayTable(tableName); // Refresh view after deletion
    }

    private void showDropTableForm() {
        List<String> currentTables = getAllTableNames();
        if (currentTables.isEmpty()) {
            showMessage("No tables available in the database to drop.", true);
            return;
        }

        VBox formContainer = new VBox(10);
        formContainer.setPadding(new Insets(20));
        formContainer.setAlignment(Pos.TOP_LEFT);
        formContainer.getStyleClass().add("form-panel");

        Label selectTableLabel = new Label("Select Table to Drop:");
        ChoiceBox<String> tableChoiceBox = new ChoiceBox<>(FXCollections.observableArrayList(currentTables));
        tableChoiceBox.setPrefWidth(200);
        formContainer.getChildren().addAll(selectTableLabel, tableChoiceBox);

        Button dropTableButton = new Button("Drop Table");
        dropTableButton.setDisable(true); // Disable until a table is selected
        formContainer.getChildren().add(dropTableButton);

        tableChoiceBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newTableName) -> {
            dropTableButton.setDisable(newTableName == null);
        });

        dropTableButton.setOnAction(e -> {
            String selectedTable = tableChoiceBox.getValue();
            if (selectedTable == null) {
                showAlert(Alert.AlertType.ERROR, "Input Error", "Please select a table to drop.");
                return;
            }
            dropTable(selectedTable);
        });
        contentPane.getChildren().add(formContainer);
    }

    private void dropTable(String tableName) {
        if (connection == null) {
            showAlert(Alert.AlertType.ERROR, "Database Error", "No active database connection.");
            return;
        }
        if (!getAllTableNames().contains(tableName)) {
            showAlert(Alert.AlertType.ERROR, "Error", "Table '" + tableName + "' not found in the database.");
            return;
        }

        String sql = "DROP TABLE " + tableName;
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
            tableSchemas.remove(tableName); // Remove from cache
            showMessage("Table '" + tableName + "' dropped successfully from Oracle.", false);
            contentPane.getChildren().clear(); // Clear the form after success
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "SQL Error", "Failed to drop table '" + tableName + "': " + e.getMessage());
        }
    }

    private void showTruncateTableForm() {
        List<String> currentTables = getAllTableNames();
        if (currentTables.isEmpty()) {
            showMessage("No tables available in the database to truncate.", true);
            return;
        }

        VBox formContainer = new VBox(10);
        formContainer.setPadding(new Insets(20));
        formContainer.setAlignment(Pos.TOP_LEFT);
        formContainer.getStyleClass().add("form-panel");

        Label selectTableLabel = new Label("Select Table to Truncate:");
        ChoiceBox<String> tableChoiceBox = new ChoiceBox<>(FXCollections.observableArrayList(currentTables));
        tableChoiceBox.setPrefWidth(200);
        formContainer.getChildren().addAll(selectTableLabel, tableChoiceBox);

        Button truncateTableButton = new Button("Truncate Table");
        truncateTableButton.setDisable(true); // Disable until a table is selected
        formContainer.getChildren().add(truncateTableButton);

        tableChoiceBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newTableName) -> {
            truncateTableButton.setDisable(newTableName == null);
        });

        truncateTableButton.setOnAction(e -> {
            String selectedTable = tableChoiceBox.getValue();
            if (selectedTable == null) {
                showAlert(Alert.AlertType.ERROR, "Input Error", "Please select a table to truncate.");
                return;
            }
            truncateTable(selectedTable);
        });
        contentPane.getChildren().add(formContainer);
    }

    private void truncateTable(String tableName) {
        if (connection == null) {
            showAlert(Alert.AlertType.ERROR, "Database Error", "No active database connection.");
            return;
        }
        if (!getAllTableNames().contains(tableName)) {
            showAlert(Alert.AlertType.ERROR, "Error", "Table '" + tableName + "' not found in the database.");
            return;
        }

        String sql = "TRUNCATE TABLE " + tableName;
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
            showMessage("Table '" + tableName + "' truncated successfully (all records removed).", false);
            contentPane.getChildren().clear(); // Clear the form after success
            displayTable(tableName); // Display the now empty table
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "SQL Error", "Failed to truncate table '" + tableName + "': " + e.getMessage());
        }
    }


    private void showSelectTableForm() {
        List<String> currentTables = getAllTableNames();
        if (currentTables.isEmpty()) {
            showMessage("No tables available in the database to select. Please create a table first.", true);
            return;
        }

        VBox formContainer = new VBox(10);
        formContainer.setPadding(new Insets(20));
        formContainer.setAlignment(Pos.TOP_LEFT);
        formContainer.getStyleClass().add("form-panel");

        Label selectTableLabel = new Label("Select Table to View:");
        ChoiceBox<String> tableChoiceBox = new ChoiceBox<>(FXCollections.observableArrayList(currentTables));
        tableChoiceBox.setPrefWidth(200);
        formContainer.getChildren().addAll(selectTableLabel, tableChoiceBox);

        // Add a placeholder for the table view which will be populated dynamically
        StackPane tableViewContainer = new StackPane();
        tableViewContainer.setPadding(new Insets(10, 0, 0, 0));
        formContainer.getChildren().add(tableViewContainer);

        tableChoiceBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newTableName) -> {
            tableViewContainer.getChildren().clear(); // Clear previous table view
            if (newTableName != null) {
                TableView<Map<String, Object>> tableView = createTableView(newTableName);
                if (tableView != null) {
                    tableViewContainer.getChildren().add(tableView);
                } else {
                    showMessage("Table '" + newTableName + "' has no schema or data. Cannot display.", true);
                }
            }
        });
        contentPane.getChildren().add(formContainer);
    }

    private void displayTable(String tableName) {
        contentPane.getChildren().clear(); // Clear current content
        TableView<Map<String, Object>> tableView = createTableView(tableName);
        if (tableView != null) {
            VBox displayBox = new VBox(10);
            displayBox.setPadding(new Insets(20));
            displayBox.setAlignment(Pos.TOP_CENTER);
            Label tableTitle = new Label("Contents of Table: " + tableName);
            tableTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");
            displayBox.getChildren().addAll(tableTitle, tableView);
            contentPane.getChildren().add(displayBox);
        } else {
            showMessage("Could not display table '" + tableName + "'. It might be empty or not exist.", true);
        }
    }

    private TableView<Map<String, Object>> createTableView(String tableName) {
        if (connection == null) {
            showAlert(Alert.AlertType.ERROR, "Database Error", "No active database connection.");
            return null;
        }

        TableView<Map<String, Object>> tableView = new TableView<>();
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setPlaceholder(new Label("No records in this table."));

        ObservableList<Map<String, Object>> tableData = FXCollections.observableArrayList();

        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM " + tableName)) {

            ResultSetMetaData rsmd = rs.getMetaData();
            int columnCount = rsmd.getColumnCount();

            // Clear previous schema for this table (if any) and rebuild from DB metadata
            Map<String, String> currentTableSchema = new LinkedHashMap<>();

            // Add columns based on ResultSetMetaData
            for (int i = 1; i <= columnCount; i++) {
                String columnName = rsmd.getColumnName(i);
                String columnType = rsmd.getColumnTypeName(i); // Get Oracle-specific type name

                TableColumn<Map<String, Object>, String> column = new TableColumn<>(columnName);
                column.setCellValueFactory(cellData -> {
                    Object value = cellData.getValue().get(columnName);
                    return new SimpleStringProperty(value != null ? value.toString() : "NULL");
                });
                tableView.getColumns().add(column);
                currentTableSchema.put(columnName, columnType);
            }
            tableSchemas.put(tableName, currentTableSchema); // Cache the schema

            // Populate table data
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = rsmd.getColumnName(i);
                    Object value = rs.getObject(i);
                    row.put(columnName, value);
                }
                tableData.add(row);
            }
            tableView.setItems(tableData);
            return tableView;

        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "SQL Error", "Failed to retrieve table data for '" + tableName + "': " + e.getMessage());
            return null;
        }
    }

    // Retrieves all table names accessible to the current user
    private List<String> getAllTableNames() {
        List<String> tableNames = new ArrayList<>();
        if (connection == null) {
            return tableNames;
        }
        try (ResultSet tables = connection.getMetaData().getTables(null, connection.getSchema(), null, new String[]{"TABLE"})) {
            while (tables.next()) {
                tableNames.add(tables.getString("TABLE_NAME"));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching table names: " + e.getMessage());
            // showAlert(Alert.AlertType.ERROR, "DB Metadata Error", "Failed to retrieve table names: " + e.getMessage());
        }
        return tableNames;
    }

    // Fetches schema for a given table from the database
    private Map<String, String> getSchemaFromDatabase(String tableName) {
        Map<String, String> schema = new LinkedHashMap<>();
        if (connection == null) {
            return schema;
        }
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM " + tableName + " WHERE ROWNUM = 0")) { // Get metadata without data
            ResultSetMetaData rsmd = rs.getMetaData();
            for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                schema.put(rsmd.getColumnName(i), rsmd.getColumnTypeName(i)); // Oracle type name
            }
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "DB Schema Error", "Failed to retrieve schema for table '" + tableName + "': " + e.getMessage());
            return null;
        }
        return schema;
    }


    private Object convertType(String value, String type) throws NumberFormatException, IllegalArgumentException {
        if (value == null || value.isEmpty()) {
            return null; // Treat empty string as NULL for all types
        }
        // Base types from DATA_TYPES (e.g., VARCHAR, NUMBER, DOUBLE PRECISION, CHAR)
        switch (type.toUpperCase()) {
            case "VARCHAR":
            case "VARCHAR2": // Oracle's string type
                return value;
            case "NUMBER": // Oracle's numeric type
            case "INT": // Application-level INT type for parsing
                return Integer.parseInt(value);
            case "DOUBLE":
            case "DOUBLE PRECISION": // Oracle's floating point type
                return Double.parseDouble(value);
            case "BOOLEAN": // Application-level BOOLEAN type (will be mapped to CHAR(1) in Oracle)
            case "CHAR": // Oracle's CHAR type for boolean representation
                if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("T")) {
                    return "T"; // Store as 'T' for true
                } else if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("F")) {
                    return "F"; // Store as 'F' for false
                } else {
                    throw new IllegalArgumentException("Invalid boolean value. Expected 'true'/'false' or 'T'/'F'");
                }
            default:
                throw new IllegalArgumentException("Unsupported data type: " + type);
        }
    }


    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showMessage(String message, boolean isError) {
        messageLabel.setText(message);
        if (isError) {
            messageLabel.setTextFill(Color.RED);
        } else {
            messageLabel.setTextFill(Color.DARKGREEN);
        }
    }

    @Override
    public void stop() throws Exception {
        // Close database connection when the application exits
        if (connection != null && !connection.isClosed()) {
            connection.close();
            System.out.println("Database connection closed.");
        }
        super.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}