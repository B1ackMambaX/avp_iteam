package com.bank.avpiteam.demo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RussianNamesTest {

    @Test
    void maleGenitive() {
        assertThat(RussianNames.genitive("Михайлов", "Евгений", "Евгеньевич", false))
                .isEqualTo("Михайлова Евгения Евгеньевича");
        assertThat(RussianNames.genitive("Ильин", "Игорь", "Петрович", false))
                .isEqualTo("Ильина Игоря Петровича");
        assertThat(RussianNames.genitive("Орлов", "Илья", "Иванович", false))
                .isEqualTo("Орлова Ильи Ивановича");
    }

    @Test
    void femaleGenitive() {
        assertThat(RussianNames.genitive("Иванова", "Анна", "Сергеевна", true))
                .isEqualTo("Ивановой Анны Сергеевны");
        assertThat(RussianNames.genitive("Петрова", "Ольга", "Ивановна", true))
                .isEqualTo("Петровой Ольги Ивановны");
        assertThat(RussianNames.genitive("Соколова", "Мария", "Павловна", true))
                .isEqualTo("Соколовой Марии Павловны");
        assertThat(RussianNames.genitive("Белова", "Любовь", "Олеговна", true))
                .isEqualTo("Беловой Любови Олеговны");
    }
}
