package kardash.project.app.models;

import lombok.Getter;

import java.util.concurrent.CompletableFuture;

@Getter
public final class IncomingRequestContext {

    private final String PCName;
    private final String PCAddress;
    private final String fileName;
    private final String fileSize;

    public final CompletableFuture<Boolean> answer =  new CompletableFuture<>();

    public IncomingRequestContext(String PCName, String PCAddress, String fileName, String fileSize) {
        this.PCName = PCName;
        this.PCAddress = PCAddress;
        this.fileName = fileName;
        this.fileSize = fileSize;
    }

}
