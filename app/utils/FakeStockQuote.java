package utils;

import java.util.Random;

/**
 * Created by weili on 16-4-9.
 */
public class FakeStockQuote implements StockQuote {
    @Override
    public Double newPrice(Double lastPrice) {
        return lastPrice * (0.95 + (0.1 * new Random().nextDouble())); // lastPrice * (0.95 to 1.05)
    }
}
