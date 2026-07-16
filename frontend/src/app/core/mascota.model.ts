export interface Mascota {
  id: number;
  duenioId: number;
  duenioNombre: string;
  nombre: string;
  especie: string;
  raza: string;
  fechaNacimiento: string;
  activo: boolean;
  creadoEn: string;
  actualizadoEn: string;
}

export interface MascotaRequest {
  duenioId: number;
  nombre: string;
  especie: string;
  raza: string;
  fechaNacimiento: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

export interface MascotaFiltro {
  nombre?: string;
  especie?: string;
  raza?: string;
  page: number;
  size: number;
  sort: string;
}
