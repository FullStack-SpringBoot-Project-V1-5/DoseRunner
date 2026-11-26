package tn.rnu.eniso.doserunner.order.bo;

import org.junit.jupiter.api.Test;
import tn.rnu.eniso.doserunner.order.dao.DrugEntity;
import tn.rnu.eniso.doserunner.order.shared.error.InvalidOrderException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormularyBOTest {

    private static final Instant T0 = Instant.parse("2026-11-20T08:00:00Z");
    private static final Instant T1 = Instant.parse("2026-11-20T09:00:00Z");

    private final FormularyBO formularyBO = new FormularyBO();

    @Test
    void updateNormalisesAndStampsTheDrug() {
        DrugEntity drug = new DrugEntity("TRAM-50", "Tramadol", "CAPSULE", false, T0);

        formularyBO.update(drug, " Tramadol 50 mg ", " tablet ", true, T1);

        assertThat(drug.getName()).isEqualTo("Tramadol 50 mg");
        assertThat(drug.getForm()).isEqualTo("TABLET");
        assertThat(drug.isControlled()).isTrue();
        assertThat(drug.getUpdatedAt()).isEqualTo(T1);
    }

    @Test
    void updateRequiresNameAndForm() {
        DrugEntity drug = new DrugEntity("TRAM-50", "Tramadol", "CAPSULE", false, T0);

        assertThatThrownBy(() -> formularyBO.update(drug, " ", "TABLET", false, T1))
                .isInstanceOf(InvalidOrderException.class);
        assertThatThrownBy(() -> formularyBO.update(drug, "Tramadol", null, false, T1))
                .isInstanceOf(InvalidOrderException.class);
        assertThat(drug.getName()).isEqualTo("Tramadol");
    }
}
