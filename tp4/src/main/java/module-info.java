module tp4 {
    requires jade;
    requires java.desktop;

    exports ma.fstm.ilisi.tp4;
    opens ma.fstm.ilisi.tp4 to jade;
}