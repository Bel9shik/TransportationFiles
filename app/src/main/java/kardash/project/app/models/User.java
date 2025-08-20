package kardash.project.app.models;

import java.util.Objects;

public record User (String ip, int port, String hostName) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(ip + "$" + port + "$" + hostName, user.ip + "$" + user.port + "$" + user.hostName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ip + "$" + port + "$" + hostName);
    }
}
