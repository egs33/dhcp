(ns dhcp.protocol.database)

(defprotocol IDatabase
  (add-reservations [this reservations])
  (get-all-reservations [this])
  (find-reservation-by-id [this id])
  (find-reservations-by-hw-address [this ^bytes hw-address])
  (find-reservations-by-ip-address-range [this ^bytes start-address ^bytes end-address])
  (delete-reservation [this ^bytes hw-address])
  (delete-reservation-by-id [this id])
  (delete-reservations-by-source [this source])

  (add-lease [this lease])
  (get-all-leases [this])
  (find-leases-by-hw-address [this ^bytes hw-address])
  (find-leases-by-ip-address-range [this ^bytes start-address ^bytes end-address])
  (find-lease-by-id [this lease-id])
  (update-lease [this ^bytes hw-address ^bytes ip-address values])
  (delete-lease [this ^bytes hw-address ^bytes start-address ^bytes end-address])

  (transaction [this f]))
