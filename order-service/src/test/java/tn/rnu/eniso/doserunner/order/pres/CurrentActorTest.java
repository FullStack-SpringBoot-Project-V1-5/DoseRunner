package tn.rnu.eniso.doserunner.order.pres;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import tn.rnu.eniso.doserunner.order.config.ActorProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentActorTest {

    private final CurrentActor actor = new CurrentActor(new ActorProperties(true, "demo-nurse", "demo-pharma"));

    @Test
    void principalWinsOverHeaderAndDemoId() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setUserPrincipal(() -> "jwt-subject");
        request.addHeader(CurrentActor.USER_HEADER, "header-user");

        assertThat(actor.nurseId(request)).isEqualTo("jwt-subject");
    }

    @Test
    void headerIsUsedWhenThereIsNoPrincipal() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CurrentActor.USER_HEADER, " u-nurse-7 ");

        assertThat(actor.nurseId(request)).isEqualTo("u-nurse-7");
    }

    @Test
    void demoIdsAreTheLastResortPerRole() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(actor.nurseId(request)).isEqualTo("demo-nurse");
        assertThat(actor.pharmacistId(request)).isEqualTo("demo-pharma");
    }

    @Test
    void headerIsIgnoredWhenDisabled() {
        CurrentActor strict = new CurrentActor(new ActorProperties(false, "demo-nurse", "demo-pharma"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CurrentActor.USER_HEADER, "header-user");

        assertThat(strict.nurseId(request)).isEqualTo("demo-nurse");
    }

    @Test
    void malformedHeaderIsRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CurrentActor.USER_HEADER, "bob; drop table");

        assertThatThrownBy(() -> actor.nurseId(request)).isInstanceOf(IllegalArgumentException.class);
    }
}
