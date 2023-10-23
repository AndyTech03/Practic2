package org.example;

import be.quodlibet.boxable.BaseTable;
import be.quodlibet.boxable.Cell;
import be.quodlibet.boxable.Row;
import be.quodlibet.boxable.image.Image;
import be.quodlibet.boxable.utils.PageContentStreamOptimized;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.*;
import java.io.*;


public class Main {
    // Инициализация логера
    private static final Logger log = Logger.getLogger(Main.class);
    private static final float BOTTOM_MARGIN = 15 * 72 / 25.4f;
    private static final float LEFT_MARGIN = 15 * 72 / 25.4f;
    private static final float RIGHT_MARGIN = 15 * 72 / 25.4f;
    private static List<Consumer<String>> consumers = new ArrayList<>();
    private static final String dataFile = "consumers.txt";

    private static Connection conn;

    public static void main(String[] args)
    {
        // Настройка log4j
        String log4jConfPath = System.getProperty("user.dir") + "/log4j.properties";
        PropertyConfigurator.configure(log4jConfPath);

        // Открытие файла
        loadConsumers();

        // Подключение к БД
        try {
            connect();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Настройка PDF
        PDDocument pdDoc = new PDDocument();
        PDPage page = new PDPage(PDRectangle.A4);
        PDRectangle rectangle = page.getMediaBox();
        pdDoc.addPage(page);

        float contentWidth = rectangle.getWidth() - LEFT_MARGIN - RIGHT_MARGIN;
        InputStream fontStream = null;
        BaseTable table = null;
        PDFont font = null;
        try {
            fontStream = Main.class.getClassLoader().getResourceAsStream("arial.ttf");
            font = PDType0Font.load(pdDoc, fontStream, true);
            assert fontStream != null;
            fontStream.close();

            table = new BaseTable(745, 0,
                    BOTTOM_MARGIN, contentWidth, LEFT_MARGIN, pdDoc, page, true, true);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }

        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        while (running) {
            System.out.println("Меню:");
            System.out.println("1 - Добавить потребителя");
            System.out.println("2 - Удалить потребителя");
            System.out.println("3 - Редактировать потребителя");
            System.out.println("4 - Просмотреть потребителей");
            System.out.println("5 - Составить отчёт в PDF");
            System.out.println("6 - Взять данные с БД");
            System.out.println("7 - Загрузить данные в БД");
            System.out.println("0 - Выйти");
            System.out.println("Введите номер действия:");
            int choice = scanner.nextInt();
            scanner.nextLine();

            switch (choice) {
                case 1:
                    addConsumer(scanner);
                    saveConsumers();
                    break;
                case 2:
                    deleteConsumer(scanner);
                    saveConsumers();
                    break;
                case 3:
                    editConsumer(scanner);
                    saveConsumers();
                    break;
                case 4:
                    viewConsumers();
                    break;
                case 5:
                    try {
                        generatePdf(pdDoc, table, font);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    break;
                case 6:
                    try {
                        consumers = selectConsumers();
                        saveConsumers();
                        log.info("consumers overwriting from db");
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    break;
                case 7:
                    try {
                        refreshConsumers(consumers);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    break;
                case 0:
                    running = false;
                    break;
                default:
                    System.out.println("Некорректный выбор. Пожалуйста, выберите снова.");
            }
        }

        scanner.close();
        try {
            pdDoc.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public static void connect() throws Exception {
        if (conn != null)
            throw new RuntimeException("Connection not closed!");

        InputStream file;
        Properties property = new Properties();
        try {
            file = Main.class.getResourceAsStream("/application.properties");
            property.load(file);

            String host = property.getProperty("db_host");
            String name = property.getProperty("db_name");
            //String properties = property.getProperty("db_properties");

            String url = host + "/" + name;
            String login = property.getProperty("spring.datasource.username");
            String password = property.getProperty("spring.datasource.password");
            Class.forName("org.postgresql.Driver");

            conn = DriverManager.getConnection(url, login, password);
        } catch (IOException e) {
            System.err.println("ОШИБКА: Файл свойств отсуствует!");
        }

        if (conn == null)
            throw new Exception("Cant connect to DB!");
        else
            System.out.println("Connected!");
    }

    private static List<Consumer<String>> selectConsumers() throws SQLException {
        Statement statement = conn.createStatement();
        ResultSet set = statement.executeQuery(
                "select full_name, address from consumer"
        );
        List<Consumer<String>> result = new ArrayList<>();
        while (set.next()){
            result.add(new Consumer<>(set.getString("full_name"), set.getString("address")));
        }
        statement.close();
        log.info("selected " + result.size() + " consumers");
        return result;
    }

    private static void refreshConsumers(List<Consumer<String>> consumers) throws SQLException {
        Statement statement = conn.createStatement();
        statement.execute("delete from consumer where true");
        statement.execute("ALTER SEQUENCE \"Consumer_id_seq\" RESTART WITH 1");
        log.info("consumers cleared before update, Consumer_id_seq restarted.");
        StringBuilder query = new StringBuilder("insert into consumer (full_name, address) values");
        for (Consumer<String> consumer: consumers) {
            query.append("('")
                    .append(consumer.getFio())
                    .append("','")
                    .append(consumer.getAddress())
                    .append("'),");
        }
        query.deleteCharAt(query.length()-1);
        log.info("insert data: " + query);
        statement.execute(query.toString());
        statement.close();
        log.info("consumers updated");
    }

    private static void addConsumer(Scanner scanner) {
        System.out.print("Введите ФИО потребителя: ");
        String fio = scanner.nextLine();
        System.out.print("Введите адрес потребителя: ");
        String address = scanner.nextLine();

        Consumer<String> consumer = new Consumer<>(fio, address);
        consumers.add(consumer);
        System.out.println("Потребитель успешно добавлен.");
    }

    private static void deleteConsumer(Scanner scanner) {
        System.out.print("Введите ФИО потребителя для удаления: ");
        String fioToDelete = scanner.nextLine();

        boolean removed = consumers.removeIf(consumer -> consumer.getFio().equals(fioToDelete));

        if (removed) {
            System.out.println("Потребитель успешно удален.");
        } else {
            System.out.println("Потребитель с указанным ФИО не найден.");
        }
    }
    private static void editConsumer(Scanner scanner) {
        System.out.print("Введите ФИО потребителя для редактирования: ");
        String fioToEdit = scanner.nextLine();

        Consumer<String> consumerToEdit = consumers.stream()
                .filter(consumer -> consumer.getFio().equals(fioToEdit))
                .findFirst()
                .orElse(null);

        if (consumerToEdit != null) {
            System.out.print("Введите новое ФИО потребителя: ");
            String newFio = scanner.nextLine();
            System.out.print("Введите новый адрес потребителя: ");
            String newAddress = scanner.nextLine();

            consumerToEdit.setFio(newFio);
            consumerToEdit.setAddress(newAddress);
            System.out.println("Потребитель успешно отредактирован.");
        } else {
            System.out.println("Потребитель не найден.");
        }
    }

    private static void viewConsumers() {
        if (consumers.isEmpty()) {
            System.out.println("Список потребителей пуст.");
        } else {
            System.out.println("Список потребителей:");
            for (Consumer<String> consumer : consumers) {
                System.out.println("ФИО: " + consumer.getFio());
                System.out.println("Адрес: " + consumer.getAddress());
                System.out.println();
            }
        }
    }

    private static void generatePdf(PDDocument pdDoc, BaseTable table, PDFont font) throws IOException {
        for (Consumer<String> consumer : consumers) {
            Row<PDPage> row = table.createRow(0);
            createCell(row, 50, consumer.getFio(), font);
            createCell(row, 50, consumer.getAddress(), font);
        }

        int fontSize = 12;
        String text = "Отчет о потребителях";
        PDPageContentStream cos = new PDPageContentStream(pdDoc, pdDoc.getPage(0));
        PageContentStreamOptimized pcos = new PageContentStreamOptimized(cos);

        float width = font.getStringWidth(text) * fontSize / 1000;
        float pageWidth = pdDoc.getPage(0).getMediaBox().getWidth();
        cos.beginText();
        cos.setFont(font, fontSize);
        cos.newLineAtOffset((pageWidth - width) / 2, 750);
        cos.showText(text);
        cos.endText();

        InputStream imageStream = Main.class.getClassLoader().getResourceAsStream("picture.jpg");
        assert imageStream != null;
        Image img =  new Image(ImageIO.read(imageStream)).scaleByWidth(200);

        table.draw();
        img.draw(pdDoc, pcos, 250, 250);
        drawQR(pdDoc, pcos, 250 , 400);
        cos.close();
        try {
            File file = new File("File.pdf");
            pdDoc.save(file);
            System.out.println("Файл успешно создан.");
        } catch (IOException e) {
            System.err.println("Произошла ошибка при создании файла: " + e.getMessage());
            e.printStackTrace();
        }
    }

    static private void createCell(Row<PDPage> row, int width, String value, PDFont font) {
        Cell<PDPage> cell = row.createCell(width, value);
        cell.setFont(font);
    }

    private static void saveConsumers() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(dataFile))) {
            oos.writeObject(consumers);
        } catch (IOException e) {
            System.err.println("Ошибка при сохранении данных: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private static void loadConsumers() {
        File file = new File(dataFile);
        if (file.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(dataFile))) {
                consumers = (List<Consumer<String>>) ois.readObject();
            } catch (IOException | ClassNotFoundException e) {
                System.err.println("Ошибка при загрузке данных: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("Файл данных не найден. Создание нового файла.");
        }
    }

    private static void drawQR(PDDocument pdDoc, PageContentStreamOptimized stream, float x, float y) throws IOException {
        String stringBuf = "AndyTech" + "Hello world!" +
                "Привет мир" +
                "Spamm eggs";
        InputStream inputStream = new ByteArrayInputStream(generateQR(stringBuf));
        BufferedImage bufImage = ImageIO.read(inputStream);
        Image img = new Image(bufImage);
        img.draw(pdDoc, stream, x, y);
    }
    private static byte[] generateQR(String content) {
        ErrorCorrectionLevel errorCorrectionLevel = ErrorCorrectionLevel.H;
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 0);
        hints.put(EncodeHintType.ERROR_CORRECTION, errorCorrectionLevel);

        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();

        try (arrayOutputStream) {
            BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, 250, 250, hints);
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", arrayOutputStream);
        } catch (IOException | WriterException e) {
            throw new RuntimeException(e);
        }
        return arrayOutputStream.toByteArray();
    }
}