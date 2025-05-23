package Controller;

import static org.junit.jupiter.api.Assertions.*;

import Model.Transaction;
import Model.User;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

class TransactionControllerTest {

    private String oldUserDir;

    @BeforeEach
    void setup(@TempDir Path tempDir) {
        TransactionController.setCsvFilePathForTest(tempDir.resolve("transactions.csv").toString());
    }





    @Test
    void testHasAbnormalTransactions_abnormalFound() {
        List<Transaction> transactions = List.of(
                new Transaction("alice", "Expense", 600.0, "2024/05/15 10:00", "", "Transfer In", "", "category", "", "", "", "", ""),
                new Transaction("alice", "Expense", 400.0, "2024/05/16 12:00", "", "Expense", "", "category", "", "", "", "", "")
        );
        boolean result = TransactionController.hasAbnormalTransactions("alice", transactions);
        assertTrue(result);
    }

    @Test
    void testHasAbnormalTransactions_normal() {
        List<Transaction> transactions = List.of(
                new Transaction("alice", "Expense", 200.0, "2024/05/16 12:00", "", "Expense", "", "category", "", "", "", "", ""),
                new Transaction("alice", "Transfer Out", 400.0, "2024/05/16 12:00", "", "Transfer", "", "category", "", "", "", "", "")
        );
        boolean result = TransactionController.hasAbnormalTransactions("alice", transactions);
        assertFalse(result);
    }

    @Test
    void testHasAbnormalTransactions_nullOrEmpty() {
        assertFalse(TransactionController.hasAbnormalTransactions(null, null));
        assertFalse(TransactionController.hasAbnormalTransactions("", List.of()));
    }

    @Test
    void testImportTransactions_success() throws Exception {
        File src = new File(System.getProperty("user.dir"), "src.csv");
        File dest = new File(System.getProperty("user.dir"), "dest.csv");

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(src, StandardCharsets.UTF_8))) {
            bw.write(TransactionController.CSV_HEADER); bw.newLine();
            bw.write("user1,Transfer In,600.00,2024/05/15 10:00,merchant,Transfer,,,,,,,"); bw.newLine();
            bw.write("user1,Deposit,300.00,2024/05/16 12:00,merchant,Deposit,,,,,,,"); bw.newLine();
        }

        if (dest.exists()) dest.delete();

        int imported = TransactionController.importTransactions(src, dest.getAbsolutePath());
        assertEquals(2, imported);

        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(dest, StandardCharsets.UTF_8))) {
            String line; while ((line = br.readLine()) != null) lines.add(line);
        }
        long dataLineCount = lines.stream()
                .filter(l -> !l.trim().isEmpty() && !l.equals(TransactionController.CSV_HEADER))
                .count();
        assertEquals(2, dataLineCount);
        assertTrue(lines.stream().anyMatch(l -> l.contains("Transfer In")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("Deposit")));
    }

    @Test
    void testAddAndReadTransaction() throws Exception {
        boolean ok = TransactionController.addTransaction("user1", "Income", 500.0, "2024/05/17 13:00", "shop", "Transfer In");
        assertTrue(ok);

        List<Transaction> txs = TransactionController.readTransactions("user1");
        assertEquals(1, txs.size());
        assertEquals("user1", txs.get(0).getAccountUsername());
        assertEquals("Income", txs.get(0).getOperation());
        assertEquals(500.0, txs.get(0).getAmount());
        assertEquals("2024/05/17 13:00", txs.get(0).getTimestamp());
    }

    @Test
    void testAddTransaction_invalid() {
        // Negative amount, no operation, non-compliant fields
        assertFalse(TransactionController.addTransaction(null, "Income", 500.0, "2024/05/17 13:00", "shop", "Income"));
        assertFalse(TransactionController.addTransaction("user1", "Other", 500.0, "2024/05/17 13:00", "shop", "Income"));
        assertFalse(TransactionController.addTransaction("user1", "Income", -1, "2024/05/17 13:00", "shop", "Income"));
        assertFalse(TransactionController.addTransaction("user1", "Income", 100, null, "shop", "Income"));
    }

    @Test
    void testRemoveTransaction() throws Exception {
        TransactionController.addTransaction("user1", "Income", 100.0, "2024/05/18 10:00", "market", "Income");
        TransactionController.addTransaction("user1", "Income", 200.0, "2024/05/19 10:00", "market", "Income");
        User user = new User();
        user.setUsername("user1");
        user.setBalance(300.0);
        boolean removed = TransactionController.removeTransaction("user1", "2024/05/18 10:00", user);
        System.out.println("Removed? " + removed);
        List<Transaction> txs = TransactionController.readTransactions("user1");
        for (Transaction t : txs) {
            System.out.println("LEFT: " + t.getAccountUsername() + ", " + t.getTimestamp());
        }
        assertTrue(removed);
        assertEquals(1, txs.size());
        assertEquals("2024/05/19 10:00", txs.get(0).getTimestamp());
        double expected = TransactionController.calculateUserBalance("user1");
        assertEquals(expected, user.getBalance(), 1e-6);
    }
}
