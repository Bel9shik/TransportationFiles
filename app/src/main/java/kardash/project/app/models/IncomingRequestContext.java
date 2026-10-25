package kardash.project.app.models;

import kardash.project.transfer.TransferNode;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public record IncomingRequestContext(TransferNode.Offer offer, CompletableFuture<Path> answer) {}
