package kardash.project.app.core.services;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import kardash.project.app.DesktopTransfers;
import kardash.project.app.models.User;
import kardash.project.transfer.Discovery;

public final class UserDiscoveryService extends Service<ObservableList<User>> {
    private final ObservableList<User> users = FXCollections.observableArrayList();
    public ObservableList<User> getUsers() { return users; }

    @Override protected Task<ObservableList<User>> createTask() {
        return new Task<>() {
            @Override protected ObservableList<User> call() throws Exception {
                updateMessage("Ищем устройства в локальной сети…");
                try (Discovery discovery = new Discovery(DesktopTransfers.port(), DesktopTransfers.name(),
                        peers -> Platform.runLater(() -> {
                            if (isCancelled()) return;
                            var fresh = peers.stream().map(p -> new User(p.host(), p.port(), p.name(), System.currentTimeMillis())).toList();
                            users.removeIf(user -> !fresh.contains(user));
                            fresh.stream().filter(user -> !users.contains(user)).forEach(users::add);
                            updateMessage(users.isEmpty() ? "Ищем устройства… На получателе откройте «Принять файл»."
                                    : "Найдено устройств: " + users.size());
                        }), this::updateMessage)) {
                    while (!isCancelled()) Thread.sleep(1000);
                }
                return users;
            }
        };
    }
}
