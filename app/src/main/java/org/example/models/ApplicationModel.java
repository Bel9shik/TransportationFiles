package org.example.models;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.net.InetAddress;
import java.util.Map;

@Getter
public class ApplicationModel {

    private Map<InetAddress, String> users;

    public ApplicationModel(Map<InetAddress, String> users) {
        this.users = users;
    }

}
