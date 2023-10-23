package org.example;

import java.io.Serializable;
class Consumer<T> implements Serializable{
    private T fio;
    private T address;

    public Consumer(T fio, T address) {
        this.fio = fio;
        this.address = address;
    }

    public T getFio() {
        return fio;
    }

    public void setFio(T fio) {
        this.fio = fio;
    }

    public T getAddress() {
        return address;
    }

    public void setAddress(T address) {
        this.address = address;
    }
}