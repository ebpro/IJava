package com.example;

/**
 * A simple Greeter class that greets a person by name.
 */
public class Greeter {
    private final String name;

    public Greeter(String name) {
        this.name = name;
    }

    /**
     * Greets the person by name.
     * 
     * @return A greeting message.
     */
    public String greet() {
        return "Hello " + name;
    }
}
