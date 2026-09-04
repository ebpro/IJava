package com.example;

public class Product {
    private long id;
    private String name;
    private double price;

    public Product(long id, String name, double price) {
        this.id = id;
        this.name = name;
        this.price = price;
    }

    public static String summary(Product p) {
        return p.id + ":" + p.name + ":" + p.price;
    }
}
