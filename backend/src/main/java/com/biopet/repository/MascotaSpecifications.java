package com.biopet.repository;

import com.biopet.entity.Mascota;
import org.springframework.data.jpa.domain.Specification;

public final class MascotaSpecifications {

    private MascotaSpecifications() {}

    public static Specification<Mascota> activa() {
        return (root, query, cb) -> cb.isTrue(root.get("activo"));
    }

    public static Specification<Mascota> nombreContiene(String nombre) {
        return (root, query, cb) -> cb.like(cb.lower(root.get("nombre")), contiene(nombre));
    }

    public static Specification<Mascota> especieContiene(String especie) {
        return (root, query, cb) -> cb.like(cb.lower(root.get("especie")), contiene(especie));
    }

    public static Specification<Mascota> razaContiene(String raza) {
        return (root, query, cb) -> cb.like(cb.lower(root.get("raza")), contiene(raza));
    }

    private static String contiene(String valor) {
        return "%" + valor.toLowerCase() + "%";
    }
}
