package com.spring.entity;

import java.io.Serializable;

/**
 * @description:Entity
 * @author: Cherry
 * @time: 2020/6/13 8:11
 */
public class Role implements Serializable {
    private int id;
    private String name;
    private String note;

    public Role() {
    }

    public Role(int id, String name, String note) {
        this.id = id;
        this.name = name;
        this.note = note;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    @Override
    public String toString() {
        return "Role{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", note='" + note + '\'' +
                '}';
    }
}
