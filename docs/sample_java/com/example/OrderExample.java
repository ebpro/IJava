package com.example;

/**
 * OrderExample is a fake java class
 *
 * @see Product
 */
public class OrderExample {
    public static class Product {
        public long id;
        public String name;
        public double price;

        public Product(long id, String name, double price) {
            this.id = id;
            this.name = name;
            this.price = price;
        }
    }

    /**
     * No arg constructor
     */
    public OrderExample() {
    }

    /**
     * Returns a summary of the product.
     * 
     * @param p The product to summarize.
     * @return A string summary of the product.
     */
    public static String summary(Product p) {
        return p.id + ":" + p.name + ":" + p.price;
    }

    @Deprecated
    public static String deprecatedMethod() {
        return "This method is deprecated";
    }
}
