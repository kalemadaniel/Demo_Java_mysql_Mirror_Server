-- Exécuter sur A puis sur B, dans la base choisie.
-- Créer les bases séparément : CREATE DATABASE mirror_a CHARACTER SET utf8mb4;
-- et CREATE DATABASE mirror_b CHARACTER SET utf8mb4;
CREATE TABLE IF NOT EXISTS contacts (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  nom VARCHAR(160) NOT NULL,
  email VARCHAR(254) NULL,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Sur A seulement, puis activer la synchronisation :
-- INSERT INTO contacts (id,nom,email) VALUES (UUID(),'Contact A','contact@example.com');
-- Sur B : modifier une ligne existante pour vérifier le retour vers A.
-- UPDATE contacts SET nom='Nom modifié sur B' WHERE id='UUID_A_REMPLACER';
-- Suppression logique (propagée) :
-- UPDATE contacts SET is_deleted=1 WHERE id='UUID_A_REMPLACER';
-- Lecture des lignes actives : SELECT * FROM contacts WHERE is_deleted=0;
-- Ne pas utiliser DELETE : une ligne physiquement effacée est recréée depuis l’autre serveur.
