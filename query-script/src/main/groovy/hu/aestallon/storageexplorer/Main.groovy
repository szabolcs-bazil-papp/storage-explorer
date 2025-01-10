package hu.aestallon.storageexplorer

import com.aestallon.storageexplorer.queryscript.api.QueryScript


static void main(String[] args) {
  def qs = QueryScript.evaluate("""
    query {
          a 'User'
       from 'org'
      where { str 'name' contains 'Attila' } and { bool 'inactive' is false }
    }""")
  println qs

}

/*

}




 */