package hu.aestallon.storageexplorer

import com.aestallon.storageexplorer.arcscript.api.Arc

static void main(String[] args) {
  def arcScript = Arc.parse(/*"""
    query {
          a User
       from org
      where { 
        str attributes.name.first is 'Attila' 
      } and (expr { 
        bool inactive is false 
      } and { 
        json attributes overlaps { 'default' true }
      })
    }"""*/
    """
    query {
          a User
       from org
      where (e
        { str name is 'Foo' } and (e { num age is 9 } or { num age is 8 } )
      ) or (e
        { str name contains 'Baz' } or { json attributes overlaps { builtIn true } }
      )
      yield {
        col name as "Name"
        col username.shortVersion as "Username"
        col foo, bar
        col a.b.c, d.e.f
        col "bla"
        col "blabala", "asdff"
      }
      
      show 'xx', 'yyy'
      show 'aaaa' as "B"
      
      order {
        by name desc
        by 'username' asc
        by 'foo.bar' desc
        by x.y asc
        by y.z desc        
        by email
      }
    }""")
  def qs = Arc.evaluate arcScript
  println qs
}
