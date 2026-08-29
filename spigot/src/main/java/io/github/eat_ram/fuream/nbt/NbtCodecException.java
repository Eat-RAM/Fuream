package io.github.eat_ram.fuream.nbt;

/** Indicates that a non-empty native item tag could not be decoded safely. */
public final class NbtCodecException extends IllegalStateException {
    public NbtCodecException(String message) {
        super(message);
    }

    public NbtCodecException(String message, Throwable cause) {
        super(message, cause);
    }
}
