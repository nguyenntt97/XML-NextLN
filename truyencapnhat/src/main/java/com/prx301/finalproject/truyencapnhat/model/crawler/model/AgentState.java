package com.prx301.finalproject.truyencapnhat.model.crawler.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "AgentState", propOrder = {
        "agentName",
        "status",
        "totalUpdates",
        "totalInsert"
})
public class AgentState {
    @XmlElement(name = "name")
    private String agentName;
    @XmlElement(name = "status")
    private int status;
    @XmlElement(name = "updates")
    private int totalUpdates;
    @XmlElement(name = "inserts")
    private int totalInsert;

    public AgentState() {
    }

    public AgentState(String agentName, int status, int totalUpdates, int totalInsert) {
        this.agentName = agentName;
        this.status = status;
        this.totalUpdates = totalUpdates;
        this.totalInsert = totalInsert;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int getTotalUpdates() {
        return totalUpdates;
    }

    public void setTotalUpdates(int totalUpdates) {
        this.totalUpdates = totalUpdates;
    }

    public int getTotalInsert() {
        return totalInsert;
    }

    public void setTotalInsert(int totalInsert) {
        this.totalInsert = totalInsert;
    }
}
