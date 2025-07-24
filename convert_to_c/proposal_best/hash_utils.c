#include "common.h"

// ============================================================================
// IMPLEMENTACIONES DE FUNCIONES HASH RÁPIDAS
// ============================================================================

/**
 * Hash function FNV-1a optimizada para marcados de Petri nets.
 * Maneja correctamente las marcas omega (OMEGA = -1).
 */
uint64_t fast_hash_marking(const int *marking, int size) {
    if (!marking || size <= 0) {
        return 0;
    }
    
    // Constantes FNV-1a de 64-bit
    const uint64_t FNV_OFFSET_BASIS = 14695981039346656037ULL;
    const uint64_t FNV_PRIME = 1099511628211ULL;
    
    uint64_t hash = FNV_OFFSET_BASIS;
    
    for (int i = 0; i < size; i++) {
        // Manejar omega como valor especial pero consistente
        uint32_t value = (marking[i] == OMEGA) ? 0xFFFFFFFF : (uint32_t)marking[i];
        
        // Hash cada byte del valor
        hash ^= (value & 0xFF);
        hash *= FNV_PRIME;
        hash ^= ((value >> 8) & 0xFF);
        hash *= FNV_PRIME;
        hash ^= ((value >> 16) & 0xFF);
        hash *= FNV_PRIME;
        hash ^= ((value >> 24) & 0xFF);
        hash *= FNV_PRIME;
    }
    
    return hash;
}

/**
 * Hash function para strings usando FNV-1a.
 */
uint64_t fast_hash_string(const char *str) {
    if (!str) {
        return 0;
    }
    
    const uint64_t FNV_OFFSET_BASIS = 14695981039346656037ULL;
    const uint64_t FNV_PRIME = 1099511628211ULL;
    
    uint64_t hash = FNV_OFFSET_BASIS;
    
    while (*str) {
        hash ^= (uint8_t)*str++;
        hash *= FNV_PRIME;
    }
    
    return hash;
}