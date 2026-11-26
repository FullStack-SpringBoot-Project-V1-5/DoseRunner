package tn.rnu.eniso.doserunner.identity.pres;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tn.rnu.eniso.doserunner.identity.facade.UserAdminFacade;
import tn.rnu.eniso.doserunner.identity.facade.dto.UserView;
import tn.rnu.eniso.doserunner.identity.pres.dto.CreateUserRequest;
import tn.rnu.eniso.doserunner.identity.pres.dto.EnabledRequest;
import tn.rnu.eniso.doserunner.identity.pres.dto.RolesRequest;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    private final UserAdminFacade admin;

    public UserAdminController(UserAdminFacade admin) {
        this.admin = admin;
    }

    @GetMapping
    public List<UserView> list() {
        return admin.list();
    }

    @PostMapping
    public ResponseEntity<UserView> create(@Valid @RequestBody CreateUserRequest req) {
        UserView created = admin.create(req.username(), req.password(), req.roles());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri()).body(created);
    }

    @PutMapping("/{id}/roles")
    public UserView replaceRoles(@PathVariable UUID id, @Valid @RequestBody RolesRequest req) {
        return admin.replaceRoles(id, req.roles());
    }

    @PatchMapping("/{id}")
    public UserView setEnabled(@PathVariable UUID id, @Valid @RequestBody EnabledRequest req) {
        return admin.setEnabled(id, req.enabled());
    }
}
