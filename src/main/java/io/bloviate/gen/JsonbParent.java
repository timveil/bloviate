package io.bloviate.gen;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public class JsonbParent {
    private UUID id;
    private String name;
    private Integer count;
    private Double amount;
    private Date date;
    private String nullable;
    private Boolean worthy;
    private List<JsonbChild> children;

    public JsonbParent() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public List<JsonbChild> getChildren() {
        return children;
    }

    public void setChildren(List<JsonbChild> children) {
        this.children = children;
    }

    public String getNullable() {
        return nullable;
    }

    public void setNullable(String nullable) {
        this.nullable = nullable;
    }

    public Boolean getWorthy() {
        return worthy;
    }

    public void setWorthy(Boolean worthy) {
        this.worthy = worthy;
    }
}
