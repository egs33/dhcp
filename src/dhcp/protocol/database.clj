(ns dhcp.protocol.database)

(defprotocol IDatabase
  (add-reservations [this reservations])
  (get-all-reservations [this])
  (find-reservations-by-hw-address [this ^bytes hw-address])
  (find-reservations-by-ip-address-range [this ^bytes start-address ^bytes end-address])
  (delete-reservation [this ^bytes hw-address])

  (add-lease [this lease])
  (get-all-leases [this])
  (find-leases-by-hw-address [this ^bytes hw-address])
  (find-leases-by-ip-address-range [this ^bytes start-address ^bytes end-address])
  (update-lease [this ^bytes hw-address ^bytes ip-address values])
  (delete-lease [this ^bytes hw-address ^bytes start-address ^bytes end-address]))
