package com.example;

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

    public static String summary(Product p) {
        return p.id + ":" + p.name + ":" + p.price;
    }
}
